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
- **Roles:** only an ADMIN can create and update projects and create members (always as MEMBER, who then log in themselves). ADMIN and MEMBER otherwise collaborate equally; comments can only be edited or deleted by their author.

The frontend has sign-in and workspace registration (Phase 3.2). The access token is kept in `sessionStorage` (it survives a reload of the same tab and is gone when the tab closes), and the current user always comes from the backend (`/api/auth/me`). Signing out only forgets the token in the browser: there is no server-side logout, so the token itself stays valid until it expires (1 hour), and there are no refresh tokens. After sign-in, the dashboard (`/app`) summarizes the workspace from one request (`GET /api/workspaces/{id}/dashboard`): tickets by status, your open tickets, recent changes and every project's ticket counts. The project, ticket, board and member screens are not built yet.

Not implemented yet: the project, ticket, board and member screens, refresh tokens, server-side logout, password reset, invitations, rate limiting, role management and deployment.

With the backend running locally, the API is documented at `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`); use its Authorize button with a token from register or login.

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
