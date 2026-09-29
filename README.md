# QueueFlow

A lightweight issue tracking and project management web application for small software teams.

## Planned Tech Stack

- **Frontend:** React + TypeScript + Vite + Tailwind CSS
- **Backend:** Java 21 + Spring Boot + Maven
- **Database:** PostgreSQL
- **Security:** Spring Security + JWT
- **Persistence:** Spring Data JPA / Hibernate
- **Infrastructure:** Docker + Docker Compose
- **CI/CD:** GitHub Actions
- **API Documentation:** OpenAPI / Swagger

## Status

This project is currently under development. The backend REST API (Spring Boot + PostgreSQL) is in place, with Phase 2 authentication and authorization implemented:

- **Authentication:** `POST /api/auth/register` (creates a workspace and its ADMIN) and `POST /api/auth/login` return a JWT access token (HS256, 1 hour). Every other `/api/**` endpoint requires it as `Authorization: Bearer <token>`. The API is stateless: no session or cookie. The backend requires a `JWT_SECRET` (see `.env.example`).
- **Workspace isolation:** every user belongs to one workspace and only sees its data; anything in another workspace answers 404, exactly like something that does not exist.
- **Roles:** only an ADMIN can create and update projects and create, rename and remove members (always as MEMBER, who then log in themselves). ADMIN and MEMBER otherwise collaborate equally; comments can only be edited or deleted by their author.

The frontend has sign-in and workspace registration (Phase 3.2). The access token is kept in `sessionStorage` (it survives a reload of the same tab and is gone when the tab closes), and the current user always comes from the backend (`/api/auth/me`). Signing out only forgets the token in the browser: there is no server-side logout, so the token itself stays valid until it expires (1 hour), and there are no refresh tokens. After sign-in, the dashboard (`/app`) summarizes the workspace from one request (`GET /api/workspaces/{id}/dashboard`): tickets by status, your open tickets, recent changes and every project's ticket counts. The projects page (`/app/projects`) lists the workspace's projects; an ADMIN can create them and edit their name and description (a project's key cannot be changed, and projects cannot be deleted). Each project's page (`/app/projects/CORE`) lists its tickets, with search and filters by status, priority, assignee and label kept in the URL, and lets any member create tickets. Its board (`/app/projects/CORE/board`) shows the same tickets in one column per status: a ticket moves to another status by dragging it (mouse, touch or keyboard) or with its Move control, and the same search and priority, assignee and label filters apply (the columns are the statuses). A ticket's page (`/app/projects/CORE/tickets/7`) edits its title, description, status, priority and assignee in place, and adds, removes or creates labels. Below the description, any member can comment on the ticket (plain text); only a comment's author can edit or delete it, ADMIN included. The ticket's activity lists, oldest first, what the backend recorded automatically: its creation and every change of title, description, status, priority, assignee and labels (including moves on the board). The members page (`/app/members`) lists everyone in the workspace with their email and role, for both roles. An ADMIN can also add a member: they enter the person's name, email and an initial password, and the account is created straight away as a MEMBER who can sign in immediately. QueueFlow sends no email, so the admin passes the email and password on themselves. An ADMIN can also rename another member or remove them from the workspace: a removed member can no longer sign in, any token they hold stops working, their assigned tickets become unassigned, and the tickets, comments and activity they created stay. The page only offers what the current user's role allows, and the backend enforces the same rules. The whole interface is available in English and Spanish, chosen from the account menu (or on the sign-in pages) and remembered in the browser; English is the default.

Not implemented yet: email invitations, suspending members, changing roles, changing or resetting passwords, refresh tokens, server-side logout, rate limiting and deployment.

With the backend running locally, the API is documented at `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`); use its Authorize button with a token from register or login.

## Running QueueFlow

Both ways read the repository root `.env`: copy `.env.example` to `.env` and fill it in (at least `POSTGRES_PASSWORD`, the matching `DB_PASSWORD` and your own `JWT_SECRET`, e.g. from `openssl rand -base64 32`). `.env` is git-ignored; never commit it.

| | Local development | Production-style Docker stack |
|---|---|---|
| Start | `.\dev.ps1` | `docker compose -f compose.prod.yaml up -d --build` |
| Runs | PostgreSQL in Docker (`compose.yaml`); Spring Boot and the Vite dev server on this machine | PostgreSQL, the backend and the nginx-served frontend, all in containers |
| Open | `http://localhost:5173` (API on `:8080`, Swagger UI at `/swagger-ui.html`) | `http://localhost:8088` |
| Use it for | Coding, hot reload, running the tests | Trying the built application the way it runs in production |

The two use separate databases and no common ports, so they can run at the same time.

### Production-style Docker stack

Requires Docker with Docker Compose v2.

```sh
docker compose -f compose.prod.yaml up -d --build   # build the images and start
docker compose -f compose.prod.yaml ps              # all three services should become "healthy"
docker compose -f compose.prod.yaml logs -f backend # follow a service's logs
docker compose -f compose.prod.yaml down            # stop; the stack's data is kept
```

Open `http://localhost:8088` and create a workspace. Only the frontend container publishes a port, bound to this machine only (`127.0.0.1`; set `APP_PORT` to change it): nginx serves the built app and forwards `/api/` and `/actuator/health` to the backend over the stack's private network, so the browser talks to one origin. The backend and PostgreSQL are not reachable from the host, and Swagger UI is not exposed through nginx.

The backend applies the Flyway migrations when it starts. The stack keeps its database in its own Docker volume (`queueflow-stack_postgres-data`), which survives `down` and restarts and is separate from the development database.

To start again from an empty database, remove the stack together with its volume. **This permanently deletes all data in the Docker stack** (the development database is not affected):

```sh
docker compose -f compose.prod.yaml down -v
```

## Frontend (local development)

Requires Node.js; the backend must be running for the connectivity check.

1. Set `VITE_API_BASE_URL` (the backend origin, e.g. `http://localhost:8080`) in the repository root `.env` (copied from `.env.example`). The frontend reads that same file; only `VITE_`-prefixed variables reach the browser.
2. From `frontend/`:

   ```sh
   npm install
   npm run dev
   ```

3. Open `http://localhost:5173` (the origin the backend's CORS allows): it leads to sign-in, or `/register` to create a workspace. `/health` shows the backend connectivity.

Other scripts: `npm run lint` (oxlint) and `npm run build` (type-check and production build).
