# syntax=docker/dockerfile:1

# QueueFlow frontend: the Vite production build, served by an unprivileged
# nginx that also proxies /api and /actuator/health to the backend (see
# docker/nginx.conf). No Node and no Vite dev server at run time.

# ---------------------------------------------------------------- build ----
FROM node:24-alpine AS build
WORKDIR /app

# Dependencies first: this layer only changes with the lockfile. npm's
# download cache is a BuildKit cache mount, reused across builds.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .

# Where the app reaches the API, fixed into the bundle at build time. "/"
# means the page's own origin: nginx proxies /api to the backend, so the
# image works under any host name without being rebuilt. Override with
# --build-arg only for an API served from another origin.
ARG VITE_API_BASE_URL=/
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

# -------------------------------------------------------------- runtime ----
# nginx as a non-root user (uid 101), listening on 8080.
FROM nginxinc/nginx-unprivileged:1.28-alpine

COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/proxy-headers.conf docker/security-headers.conf /etc/nginx/snippets/
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

# nginx itself is serving the app. Deliberately not the backend's health:
# the order the services start in is Compose's job.
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/ || exit 1
