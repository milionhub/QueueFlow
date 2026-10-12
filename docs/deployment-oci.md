# Despliegue de QueueFlow en Oracle Cloud (OCI)

Guía operativa para publicar QueueFlow con HTTPS en **una única VM** de Oracle Cloud Infrastructure usando Docker Compose. Reutiliza el stack de producción existente (`compose.prod.yaml`) y le añade un override (`compose.oci.yaml`) con Caddy como punto de entrada público.

> **Estado:** preparado en el repositorio y probado en local (Docker Desktop, 12/10/2026). **Todavía no se ha desplegado en OCI.** Todo lo que depende de la VM real está marcado como ⏳ *pendiente de validar*.

Leyenda: ✅ verificado en local · ⏳ pendiente de validar en la VM real

## Índice

1. [Arquitectura](#1-arquitectura)
2. [Ficheros del repositorio](#2-ficheros-del-repositorio)
3. [Requisitos y supuestos](#3-requisitos-y-supuestos)
4. [Dominio y HTTPS](#4-dominio-y-https)
5. [Preparar la infraestructura en OCI](#5-preparar-la-infraestructura-en-oci)
6. [Preparar la VM](#6-preparar-la-vm)
7. [Configurar el `.env` del servidor](#7-configurar-el-env-del-servidor)
8. [Desplegar](#8-desplegar)
9. [Comprobaciones de salud](#9-comprobaciones-de-salud)
10. [Actualizar](#10-actualizar)
11. [Copias de seguridad y restauración](#11-copias-de-seguridad-y-restauración)
12. [Rollback](#12-rollback)
13. [Reinicio de la VM](#13-reinicio-de-la-vm)
14. [Seguridad: qué es del repositorio y qué es de OCI](#14-seguridad-qué-es-del-repositorio-y-qué-es-de-oci)
15. [Solución de problemas](#15-solución-de-problemas)
16. [Límites y riesgos de Always Free](#16-límites-y-riesgos-de-always-free)
17. [Resumen: verificado frente a pendiente](#17-resumen-verificado-frente-a-pendiente)

## 1. Arquitectura

```text
Internet
   │  80/tcp (redirección + reto ACME)   443/tcp (HTTPS)   443/udp (HTTP/3)
   ▼
┌──────────────────────── VM OCI (Docker Compose, proyecto queueflow-stack) ───────────────────────┐
│                                                                                                  │
│  caddy (caddy:2.11-alpine)        ← único contenedor con puertos publicados                      │
│    · certificado TLS automático para QUEUEFLOW_DOMAIN y renovación                               │
│    · HTTP → HTTPS (308), HSTS                                                                    │
│    │  http, red interna                                                                          │
│    ▼                                                                                             │
│  frontend (nginx sin privilegios, :8080)                                                         │
│    · SPA estática de Vite                                                                        │
│    · /api/ y /actuator/health → backend                                                          │
│    │                                                                                             │
│    ▼                                                                                             │
│  backend (Spring Boot, JRE 21, :8080, sin puertos publicados)                                    │
│    │                                                                                             │
│    ▼                                                                                             │
│  postgres (PostgreSQL 18, sin puertos publicados) ── volumen queueflow-stack_postgres-data       │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### Por qué Caddy delante del nginx existente

| Opción | Ventajas | Inconvenientes |
|---|---|---|
| **Caddy delante (elegida)** | HTTPS automático (obtención y renovación) sin cron ni certbot; configuración de ~15 líneas; el nginx y la imagen frontend ya probados no cambian de papel | Un contenedor más (~15 MiB de RAM medidos en local) |
| nginx existente + certbot | Un proceso menos | Hay que montar certbot, sus volúmenes, la renovación periódica y la recarga de nginx; más piezas que mantener |

nginx sigue haciendo lo que ya hacía (servir la SPA y enrutar `/api/`); Caddy solo termina TLS.

### Cabeceras reenviadas (cambio necesario en nginx)

Detrás de Caddy, nginx recibe HTTP plano. Antes de este cambio, nginx enviaba al backend `X-Forwarded-Proto: http` y `X-Forwarded-Port: 80`, así que Spring Boot creía que la petición era `http://dominio:80` mientras el navegador enviaba `Origin: https://dominio`. Spring la trataba como CORS de otro origen y respondía **403** a todo `POST`/`PATCH`/`DELETE`, incluido el login. ✅ Reproducido en local.

`frontend/docker/nginx.conf` ahora toma `X-Forwarded-Proto: https` del proxy anterior si lo recibe; si no, usa su propio esquema, como antes. El acceso local por `http://localhost:8088` no cambia (✅ verificado). Un cliente que llegue a nginx directamente solo podría mal etiquetar su propia petición, y en OCI nginx no es accesible desde fuera.

## 2. Ficheros del repositorio

| Fichero | Función |
|---|---|
| `compose.prod.yaml` | Stack base (sin cambios): PostgreSQL, backend, frontend |
| `compose.oci.yaml` | Override para la VM: Caddy, quita el puerto local del frontend, límites de memoria, rotación de logs |
| `deploy/oci/Caddyfile` | Configuración de Caddy (dominio, proxy, HSTS) |
| `deploy/oci/backup.sh` | Copia de seguridad de PostgreSQL con retención |
| `.env.example` | Sección nueva con `QUEUEFLOW_DOMAIN`, `ACME_EMAIL`, `COMPOSE_FILE` y límites de memoria |
| `frontend/docker/nginx.conf`, `proxy-headers.conf` | Respetan `X-Forwarded-Proto: https` del proxy anterior |

## 3. Requisitos y supuestos

- **Arquitectura de la VM:** `x86_64` (AMD/Intel) o `aarch64` (Ampere A1). No se presupone ninguna. Todas las imágenes base publican `linux/amd64` y `linux/arm64` (✅ manifiestos comprobados), y las imágenes de QueueFlow se **construyen en la propia VM**, por lo que salen automáticamente para su arquitectura. No hay registry ni publicación de imágenes.
- **Sistema operativo:** la guía asume **Ubuntu** (24.04 o 22.04), una imagen de plataforma disponible en OCI. Con Oracle Linux los pasos de Docker y del cortafuegos cambian. ⏳
- **Docker Engine** con el plugin **Docker Compose 2.24 o superior** (necesario por la etiqueta `!reset` del override).
- **Memoria:** los límites por defecto suman como máximo 1 344 MiB (backend 768, PostgreSQL 384, Caddy 128, frontend 64). En reposo, en local, el stack completo usó unos 430 MiB (backend ~342, PostgreSQL ~66, Caddy ~15, frontend ~8). Además hace falta memoria para **construir** las imágenes (Maven y Vite). Con una VM de ~1 GB de RAM hay que añadir swap ([§6](#6-preparar-la-vm)) y probablemente bajar `BACKEND_MEM_LIMIT`. ⏳
- **Disco:** imágenes, cachés de build, base de datos, logs (rotados a 3 × 10 MB por servicio) y backups.
- **Un nombre DNS** que apunte a la IP pública de la VM ([§4](#4-dominio-y-https)).
- **Puertos 80 y 443 accesibles desde Internet.** El 80 hace falta también para el reto ACME y la redirección.

## 4. Dominio y HTTPS

Caddy pide automáticamente un certificado para `QUEUEFLOW_DOMAIN` en el primer arranque y lo renueva solo. Según su documentación, usa Let's Encrypt y, si falla, ZeroSSL. Los certificados se guardan en el volumen `caddy-data`, **que no debe borrarse**: perderlo obliga a pedirlos de nuevo y puede chocar con los límites de emisión.

Opciones de nombre, de más a menos recomendable:

1. **Dominio propio** (de pago, fuera del alcance de coste cero): un registro `A` (y `AAAA` si hay IPv6) apuntando a la IP pública.
2. **Subdominio gratuito de un servicio DNS dinámico** (p. ej. DuckDNS): se crea un subdominio y se apunta a la IP pública. ⏳
3. **DNS «comodín» basado en la IP** (p. ej. `sslip.io`, `nip.io`): nombres como `<ip-con-guiones>.sslip.io` resuelven a esa IP sin registro. ⏳

Para las opciones 2 y 3, antes de decidir conviene comprobar dos cosas:

- que el dominio base está en la [Public Suffix List](https://publicsuffix.org/list/), porque si no, los límites de emisión de Let's Encrypt se comparten entre todos sus usuarios;
- las condiciones de uso del servicio.

El HSTS que añade Caddy no incluye `includeSubDomains` ni `preload`, precisamente para no afectar a otros subdominios de un dominio compartido.

Cambiar de nombre más adelante solo requiere cambiar `QUEUEFLOW_DOMAIN` y ejecutar `docker compose up -d`. El frontend se construye con `VITE_API_BASE_URL=/` (mismo origen), así que no hay que reconstruirlo.

## 5. Preparar la infraestructura en OCI

⏳ Todo este apartado se hace en la consola de OCI y está pendiente de validar. Los nombres exactos de menús pueden variar.

1. **Red:** una VCN con una **subred pública** (el asistente de creación de VCN con conectividad a Internet crea subred, *Internet Gateway* y tabla de rutas).
2. **Instancia:** imagen Ubuntu, forma (*shape*) que muestre la etiqueta *Always Free-eligible* y que haya disponibilidad para crear. Si no hay capacidad Ampere A1, se puede usar una forma AMD; el repositorio funciona en ambas.
3. **SSH:** sube **tu clave pública**. La clave privada se queda en tu equipo; nunca va al repositorio ni a la VM.
4. **IP pública:** asigna una. Una IP pública **reservada** no cambia aunque se recree la instancia; comprueba en la documentación de Oracle si entra en tu cuota gratuita. Si usas una efímera, el DNS tendrá que actualizarse si cambia.
5. **Reglas de entrada** (*Security List* o *Network Security Group* de la subred):

   | Protocolo | Puerto | Origen | Motivo |
   |---|---|---|---|
   | TCP | 22 | **Tu IP** (`x.x.x.x/32`), no `0.0.0.0/0` si es posible | SSH |
   | TCP | 80 | `0.0.0.0/0` | Reto ACME y redirección a HTTPS |
   | TCP | 443 | `0.0.0.0/0` | HTTPS |
   | UDP | 443 | `0.0.0.0/0` | HTTP/3 (opcional; sin ella se usa HTTP/2) |

   **No** abras 5432 ni 8080: PostgreSQL y el backend no publican puertos.

6. **Cortafuegos del sistema operativo:** las imágenes de plataforma de OCI traen un cortafuegos propio en la VM, además de las reglas de la VCN. Hay que comprobar que permite 80 y 443 ([§6](#6-preparar-la-vm), paso 4).

## 6. Preparar la VM

⏳ Pendiente de validar en la VM real.

1. **Conectarse y actualizar:**

   ```bash
   ssh ubuntu@<IP_PUBLICA>
   sudo apt update && sudo apt upgrade -y
   ```

2. **Instalar Docker Engine y el plugin Compose** siguiendo la guía oficial para Ubuntu: <https://docs.docker.com/engine/install/ubuntu/>. Después, comprobar:

   ```bash
   docker --version
   docker compose version     # debe ser 2.24 o superior
   uname -m                   # x86_64 o aarch64
   sudo systemctl is-enabled docker   # "enabled": Docker arranca con la VM
   ```

   Opcional: `sudo usermod -aG docker $USER` (y volver a iniciar sesión) para no usar `sudo` con Docker. Ten en cuenta que pertenecer al grupo `docker` equivale en la práctica a tener privilegios de root.

3. **Swap** (recomendable con poca RAM; ajusta el tamaño al disco disponible):

   ```bash
   sudo fallocate -l 2G /swapfile
   sudo chmod 600 /swapfile
   sudo mkswap /swapfile
   sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   free -h
   ```

4. **Cortafuegos del sistema:** revisa las reglas existentes antes de tocar nada:

   ```bash
   sudo iptables -L INPUT -n --line-numbers
   sudo iptables -L FORWARD -n --line-numbers
   ```

   Los puertos publicados por Docker se gestionan con sus propias cadenas de iptables (`DOCKER`, `DOCKER-USER`). Si después del despliegue `https://` no responde desde fuera, pero `curl -kI https://localhost` funciona dentro de la VM, la causa está en las reglas de la VCN o en este cortafuegos. Si hay que añadir reglas, hazlo de forma persistente con la herramienta que use la imagen (en Ubuntu, el paquete `iptables-persistent` y `/etc/iptables/rules.v4`).

5. **Clonar el repositorio:**

   ```bash
   git clone https://github.com/milionhub/QueueFlow.git
   cd QueueFlow
   ```

## 7. Configurar el `.env` del servidor

El `.env` vive **solo en la VM**, no está versionado (`.gitignore`) y nunca debe copiarse al repositorio ni a una imagen (los `.dockerignore` lo excluyen).

```bash
cp .env.example .env
chmod 600 .env
openssl rand -base64 32   # → JWT_SECRET
openssl rand -base64 24   # → POSTGRES_PASSWORD
nano .env
```

Valores necesarios en el servidor:

| Variable | Valor |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER` | p. ej. `queueflow` |
| `POSTGRES_PASSWORD` | Aleatoria (salida de `openssl`) |
| `JWT_SECRET` | Aleatoria, Base64 de 32 bytes (salida de `openssl`) |
| `QUEUEFLOW_DOMAIN` | El nombre público, sin `https://` (p. ej. `queueflow.example.com`) |
| `ACME_EMAIL` | Email de contacto para la autoridad de certificación |
| `COMPOSE_FILE` | `compose.prod.yaml:compose.oci.yaml` (así basta con `docker compose …`) |
| `BACKEND_MEM_LIMIT`, `POSTGRES_MEM_LIMIT`, `FRONTEND_MEM_LIMIT`, `CADDY_MEM_LIMIT` | Opcionales; por defecto `768m`, `384m`, `64m`, `128m` |

No se usan en el servidor: `DB_*` (el stack las define), `VITE_API_BASE_URL` (la imagen usa `/`), `QUEUEFLOW_CORS_ALLOWED_ORIGINS` (todo va por el mismo origen) ni `APP_PORT` (el override retira ese puerto). Pueden quedarse comentadas.

> `POSTGRES_PASSWORD` solo se aplica cuando se crea el volumen de la base de datos. Cambiarla después en `.env` no cambia la contraseña de la base de datos existente.

Para comprobar la configuración sin mostrar secretos, `docker compose config --quiet` no imprime nada si todo es correcto, o el nombre de la variable que falta.

## 8. Desplegar

Con `COMPOSE_FILE` definido en `.env`, desde la raíz del repositorio:

```bash
docker compose config --quiet          # valida; falla si falta QUEUEFLOW_DOMAIN, JWT_SECRET, etc.
docker compose build backend           # de uno en uno para limitar el pico de memoria
docker compose build frontend
docker compose up -d --wait            # espera a que los servicios estén healthy
docker compose ps
docker compose logs -f caddy           # ver la obtención del certificado (Ctrl+C para salir)
```

Sin `COMPOSE_FILE`, todos los comandos llevan `-f compose.prod.yaml -f compose.oci.yaml`.

Orden de arranque (✅ verificado): PostgreSQL *healthy* → backend (Flyway aplica las migraciones) *healthy* → frontend *healthy* → Caddy. El healthcheck del backend tiene 60 s de margen inicial; en una VM pequeña la primera arrancada puede tardar más. ⏳

La primera vez, crea el workspace desde `https://<dominio>/register`. Quien lo registre será su ADMIN.

## 9. Comprobaciones de salud

```bash
docker compose ps                                   # los 4 servicios "healthy"
curl -sI http://<dominio>/ | head -3                # 308 → https://<dominio>/
curl -s https://<dominio>/actuator/health           # {"groups":["liveness","readiness"],"status":"UP"}
curl -sI https://<dominio>/ | grep -i strict        # cabecera HSTS
docker stats --no-stream                            # memoria frente a límites
```

Desde fuera de la VM, `nc -vz <IP_PUBLICA> 5432` y `nc -vz <IP_PUBLICA> 8080` **deben fallar**.

Resultados obtenidos en local con `QUEUEFLOW_DOMAIN=localhost` (Caddy usa entonces su CA interna, no Let's Encrypt): ✅ 308 de HTTP a HTTPS, ✅ HSTS y cabeceras de seguridad, ✅ `/actuator/health` UP, ✅ registro `201` con `Location: https://…`, ✅ login `200`, ✅ las rutas de la SPA responden, ✅ Swagger y `/v3/api-docs` no se exponen (nginx devuelve la SPA), ✅ solo Caddy publica puertos.

## 10. Actualizar

```bash
sh deploy/oci/backup.sh                 # siempre antes de actualizar
git pull
docker compose build backend
docker compose build frontend
docker compose up -d --wait
docker compose ps
```

`up -d` recrea solo los contenedores cuya imagen o configuración ha cambiado; habrá unos segundos (o el arranque del backend) sin servicio. Las migraciones nuevas de Flyway se aplican al arrancar el backend. Para liberar disco, `docker image prune` elimina solo imágenes *dangling* (sin etiqueta); **no** uses `docker system prune --volumes` ni `docker volume prune`, que podrían borrar la base de datos o los certificados.

## 11. Copias de seguridad y restauración

### Backup (✅ verificado en local)

```bash
sh deploy/oci/backup.sh
```

Genera `backups/queueflow-<fecha UTC>.dump` (formato *custom* de `pg_dump`, comprimido) con permisos `600` en un directorio `700`, y borra los dumps de más de 14 días (`KEEP_DAYS`). `backups/` está en `.gitignore`. Las credenciales se leen dentro del contenedor y nunca se imprimen.

Programarlo a diario con `crontab -e` (ajusta la ruta del repositorio): ⏳

```cron
0 3 * * * cd /home/ubuntu/QueueFlow && sh deploy/oci/backup.sh >> backups/backup.log 2>&1
```

**Importante:** un backup en el mismo disco de la VM no protege contra la pérdida de la VM. Copia los dumps fuera periódicamente, por ejemplo a tu equipo:

```bash
scp ubuntu@<IP_PUBLICA>:QueueFlow/backups/queueflow-*.dump ./
```

### Restauración (✅ verificada en local)

Sustituye el contenido actual de la base de datos por el del dump:

```bash
docker compose stop backend
docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < backups/queueflow-AAAAMMDDTHHMMSSZ.dump
docker compose up -d --wait backend
```

En la prueba local, un usuario creado después del backup dejó de existir tras restaurar y el usuario incluido en el dump siguió iniciando sesión.

## 12. Rollback

Flyway solo avanza: no deshace migraciones. Por eso:

- **Si la versión nueva no incluía migraciones:** basta con volver al commit anterior y reconstruir.

  ```bash
  git log --oneline -5
  git checkout <commit-anterior>
  docker compose build backend && docker compose build frontend
  docker compose up -d --wait
  ```

- **Si incluía migraciones:** además hay que restaurar el backup hecho antes de actualizar ([§11](#11-copias-de-seguridad-y-restauración)), porque el código anterior puede no coincidir con el esquema nuevo y Hibernate (`ddl-auto: validate`) impediría el arranque. Se pierden los datos escritos después de ese backup.

Para volver a la rama principal después: `git checkout main && git pull`.

## 13. Reinicio de la VM

Todos los servicios tienen `restart: unless-stopped` y Docker está habilitado como servicio del sistema, así que tras un reinicio los contenedores deberían volver a arrancar solos con sus volúmenes intactos (base de datos y certificados). Un contenedor parado a mano con `docker compose stop` no vuelve a arrancar hasta un `docker compose up -d`. ⏳ Comprobar una vez con `sudo reboot` y después `docker compose ps`.

## 14. Seguridad: qué es del repositorio y qué es de OCI

**Resuelto en el repositorio:**

- Solo Caddy publica puertos (80/443). Frontend, backend y PostgreSQL solo son accesibles por la red interna de Docker (✅ verificado con `docker compose port`).
- TLS automático, redirección HTTP→HTTPS y HSTS. La configuración TLS es la predeterminada de Caddy; no se ha personalizado.
- Secretos solo en el `.env` local de la VM, con fallo explícito si falta alguno. No hay secretos en imágenes ni en el repositorio.
- Contenedores: backend con usuario no root (uid 10001); nginx sin privilegios (uid 101); Caddy con `cap_drop: ALL` + `NET_BIND_SERVICE` y `no-new-privileges`.
- Spring Boot detrás del proxy: solo confía en `X-Forwarded-*` de direcciones privadas (redes Docker), y CORS sigue sin comodines.
- Reinicio automático, healthchecks en los 4 servicios, límites de memoria y rotación de logs.
- Swagger UI y `/v3/api-docs` no se exponen públicamente.

**A configurar en OCI / la VM (fuera del repositorio):** VCN y subred pública, reglas de entrada (22 restringido, 80, 443), cortafuegos del sistema operativo, IP pública (reservada o efímera), claves SSH, actualizaciones del sistema, DNS del dominio, cron de backups y copia de los backups fuera de la VM.

**Pendiente a nivel de aplicación (conocido, fuera de esta fase):** no hay *rate limiting* en login ni registro, y el registro de workspaces está abierto a cualquiera que conozca la URL.

## 15. Solución de problemas

| Síntoma | Causa probable y solución |
|---|---|
| `QUEUEFLOW_DOMAIN must be set` (o `JWT_SECRET`, etc.) | Falta la variable en `.env`. |
| Error de sintaxis con `!reset` | Docker Compose inferior a 2.24; actualizar el plugin. |
| Caddy no obtiene el certificado (`docker compose logs caddy`) | El DNS no apunta aún a la IP pública, los puertos 80/443 están cerrados en la VCN o en el cortafuegos de la VM, o se han alcanzado los límites de emisión. Comprueba con `dig +short <dominio>`. |
| Funciona `curl -kI https://localhost` dentro de la VM pero no desde fuera | Reglas de entrada de la VCN o cortafuegos del sistema ([§5](#5-preparar-la-infraestructura-en-oci), [§6](#6-preparar-la-vm)). |
| `403` en login o en cualquier `POST` | El proxy no envía `X-Forwarded-Proto: https`. Comprueba que la imagen frontend se ha reconstruido con el `nginx.conf` actual. |
| Un contenedor se reinicia en bucle | `docker inspect --format '{{.State.OOMKilled}}' <contenedor>`; si es `true`, sube su límite de memoria o añade swap. |
| La build se queda sin memoria o es muy lenta | Construye los servicios de uno en uno y añade swap ([§6](#6-preparar-la-vm)). |
| El backend no pasa a *healthy* | `docker compose logs backend`: base de datos, `JWT_SECRET` inválido o migraciones. |
| Fallo de autenticación de PostgreSQL tras cambiar `POSTGRES_PASSWORD` | La contraseña solo se aplica al crear el volumen ([§7](#7-configurar-el-env-del-servidor)). |

## 16. Límites y riesgos de Always Free

Lo que ofrece la capa gratuita (formas, CPU, memoria, almacenamiento, tráfico, IPs) lo define Oracle y puede cambiar. **Consúltalo en la documentación oficial de *Always Free Resources* de OCI antes de crear recursos**. Esta guía no garantiza ninguna capacidad ni coste.

Riesgos conocidos a revisar:

- **Capacidad:** la creación de instancias Ampere A1 puede fallar por falta de capacidad en la región. QueueFlow no depende de ARM64.
- **Recuperación de instancias inactivas:** Oracle documenta una política para recuperar instancias Always Free con uso muy bajo. Revisa sus condiciones actuales.
- **Tipo de cuenta:** las condiciones pueden diferir entre una cuenta de prueba gratuita y una de pago por uso. Vigila que solo se creen recursos marcados como *Always Free-eligible*.
- **Recursos pequeños:** con ~1 GB de RAM, construir y ejecutar el stack exige swap y límites ajustados, y el rendimiento será modesto.
- **Sin alta disponibilidad:** una sola VM, una sola base de datos y backups locales salvo que se copien fuera.

## 17. Resumen: verificado frente a pendiente

| Elemento | Estado |
|---|---|
| Configuración Compose combinada válida y fallo explícito si faltan variables | ✅ local |
| Sintaxis de Caddyfile (`caddy validate`) y nginx (`nginx -t`) | ✅ local |
| Stack completo con Caddy: 4 servicios *healthy*, HTTPS, redirección, HSTS, registro y login | ✅ local (CA interna de Caddy, dominio `localhost`) |
| Corrección de `X-Forwarded-Proto` (403 antes, 200 después) | ✅ local |
| Acceso local por `http://localhost:8088` sin cambios | ✅ local |
| Backup y restauración | ✅ local |
| Imágenes base disponibles para `linux/amd64` y `linux/arm64` | ✅ manifiestos |
| Build de las imágenes QueueFlow para `linux/arm64` (incluidos los binarios nativos de npm) y arranque básico | ✅ local con emulación QEMU, no en hardware ARM real |
| Ejecución del stack completo en `linux/arm64` | ⏳ |
| Certificado público real (Let's Encrypt/ZeroSSL) | ⏳ |
| Red, reglas de entrada y cortafuegos de OCI | ⏳ |
| Instalación de Docker, swap y memoria suficiente en la VM | ⏳ |
| Arranque tras reinicio de la VM | ⏳ |
| Cron de backups | ⏳ |
