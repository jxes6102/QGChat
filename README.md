# QGChat

QGChat is a full-stack chat application with account authentication, user profiles, direct conversations, group conversations, group member management, message history, read status, and STOMP WebSocket messaging.

## Tech Stack

- Backend: Java 17, Spring Boot 3.3, Spring Web, Spring Security, Spring Data JPA, WebSocket/STOMP
- Database: PostgreSQL
- Frontend: Nuxt 4, Vue 3, Pinia, Nuxt UI, Tailwind CSS
- Auth: Opaque Bearer session tokens with BCrypt password hashing

## Project Structure

```text
.
|-- backend/                 # Spring Boot backend service
|   |-- pom.xml
|   `-- src/main/
|       |-- java/training/QGChat/
|       |   |-- auth/        # Login, register, logout, password reset
|       |   |-- chat/        # Conversations, messages, WebSocket handling
|       |   |-- profile/     # Profile and password updates
|       |   |-- SecurityConfig.java
|       |   `-- Test.java    # Spring Boot entry point
|       `-- resources/
|           |-- application.properties
|           `-- static/ws-test.html
|-- frontend/                # Nuxt single-page frontend
|   |-- app/
|   |   |-- pages/
|   |   |-- composables/
|   |   |-- config/
|   |   `-- types/
|   |-- package.json
|   `-- nuxt.config.ts
`-- sql/
    `-- qgchat_schema.sql    # PostgreSQL schema
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL 14+

## Database Setup

Create a PostgreSQL database, then apply the schema:

```powershell
createdb qgchat
psql -d qgchat -f sql/qgchat_schema.sql
```

The schema creates the main tables for users, sessions, friendships, groups, conversations, messages, password reset tokens, email logs, and read receipts.

## Backend Setup

From the repository root:

```powershell
cd backend
```

Set the backend port and database connection before starting the app. Keep real passwords and production URLs out of committed files:

```powershell
$env:SERVER_PORT = "8082"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/qgchat"
$env:DB_USERNAME = "<database_user>"
$env:DB_PASSWORD = "<database_password>"
mvn spring-boot:run
```

The backend will be available at:

```text
http://localhost:8082
```

Health check endpoint:

```text
GET /test
```

Useful backend environment variables:

```powershell
$env:SERVER_PORT = "8082"
$env:DB_URL = "jdbc:postgresql://localhost:5432/qgchat"
$env:DB_USERNAME = "<database_user>"
$env:DB_PASSWORD = "<database_password>"
$env:QGCHAT_AUTH_TOKEN_TTL_HOURS = "12"
$env:QGCHAT_AUTH_PASSWORD_RESET_TTL_MINUTES = "15"
$env:QGCHAT_WEBSOCKET_ALLOWED_ORIGINS = "http://localhost:3000,http://127.0.0.1:3000"
```

> Note: The frontend defaults to `http://localhost:8082/api` and `ws://localhost:8082/ws/chat`, so keep the backend on port `8082` unless you also update the frontend environment variables.

## Frontend Setup

Open another terminal:

```powershell
cd frontend
npm install
npm run dev
```

The frontend development server starts at:

```text
http://localhost:3000
```

Optional frontend environment variables:

```powershell
$env:NUXT_PUBLIC_API_BASE = "http://localhost:8082/api"
$env:NUXT_PUBLIC_WS_BASE = "ws://localhost:8082/ws/chat"
```

## API Overview

Authentication:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/logout
```

Profile:

```text
GET   /api/profile
PATCH /api/profile
PATCH /api/profile/password
```

Chat:

```text
GET  /api/chats/conversations
POST /api/chats/direct
POST /api/chats/groups
POST /api/chats/groups/{groupId}/members
GET  /api/chats/conversations/{conversationId}/messages
POST /api/chats/conversations/{conversationId}/messages
POST /api/chats/conversations/{conversationId}/read
```

WebSocket/STOMP:

```text
Endpoint:    ws://localhost:8082/ws/chat
Send to:     /app/chat.send
Subscribe:   /topic/conversations/{conversationId}
```

Most authenticated REST and STOMP requests should include:

```text
Authorization: Bearer <accessToken>
```

For STOMP clients, send the header on `CONNECT`. Messages sent to `/app/chat.send` may also include the same header, but the server can reuse the authenticated STOMP session.

Do not commit real access tokens, database passwords, production hostnames, or private environment files. Use placeholders in examples and keep machine-specific values in environment variables or an untracked `application-local.properties`.

## Frontend Scripts

Run these inside `frontend/`:

```powershell
npm run dev             # Start development server
npm run build           # Build for production
npm run preview         # Preview production build
npm run generate        # Generate static output
npm run serve:generate  # Serve generated static output on port 3001
```

## Backend Commands

Run these inside `backend/`:

```powershell
mvn spring-boot:run     # Start backend
mvn test                # Run tests
mvn package             # Build jar
```

## Development Notes

- CORS currently allows `http://localhost:3000` and `http://127.0.0.1:3000`.
- WebSocket origins are controlled by `qgchat.websocket.allowed-origins` / `QGCHAT_WEBSOCKET_ALLOWED_ORIGINS`.
- Hibernate DDL generation is disabled; initialize or update the database through SQL scripts.
- `frontend/app/config/sourceUrls.ts` defines the default backend API and WebSocket URLs.
- Browser tokens are stored in `localStorage` under `qgchat.token`.
