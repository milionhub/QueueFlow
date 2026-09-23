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

Not implemented yet: refresh tokens, logout, password reset, invitations, rate limiting, role management, the frontend and deployment.

With the backend running locally, the API is documented at `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`); use its Authorize button with a token from register or login.
