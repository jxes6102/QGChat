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
|   |   |-- types/
|   |   `-- utils/          # Error localization and form validation helpers
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
GET  /api/chats/groups/{groupId}/members
PATCH /api/chats/groups/{groupId}/members/{memberUserId}/role
DELETE /api/chats/groups/{groupId}/members/{memberUserId}
POST /api/chats/groups/{groupId}/leave
PATCH /api/chats/groups/{groupId}/owner
GET  /api/chats/conversations/{conversationId}/messages
POST /api/chats/conversations/{conversationId}/messages
POST /api/chats/conversations/{conversationId}/read
```

Group management rules:

- Group `OWNER` and `ADMIN` users can add members.
- Only the group `OWNER` can promote or demote members.
- `ADMIN` users can remove normal `MEMBER` users only.
- `OWNER` must transfer ownership before leaving a group.
- Removing or leaving a group also removes the user from the matching conversation participants list.

API errors use a stable code plus a display message:

```json
{
  "code": "GROUP_OWNER_REQUIRED",
  "message": "只有群組擁有者可以執行此操作"
}
```

The frontend primarily uses `code` for predictable handling and can display `message` as a fallback. Validation errors from request DTOs are normalized to the same shape with `VALIDATION_FAILED`.

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

## Message Flow

- Historical messages are loaded through REST: `GET /api/chats/conversations/{conversationId}/messages`.
- New messages are delivered through WebSocket topic subscriptions: `/topic/conversations/{conversationId}`.
- When sending through REST, the backend persists the message and broadcasts it to the conversation topic.
- The frontend avoids duplicate rendering by using the WebSocket event when connected, and only falls back to the REST response when the socket is offline.
- Messages are sorted from oldest to newest so the latest message appears at the bottom of the chat panel.

## Frontend Validation

The frontend performs lightweight validation before calling the API:

- Login requires account and password.
- Registration checks username format, email format, display name length, password length, and password confirmation.
- Direct conversations require a valid username.
- Group creation checks group name, optional avatar URL, member username format, and duplicate member entries.
- Profile updates check display name, email format, and optional avatar URL.
- Adding group members checks username format and removes duplicate comma-separated entries.

The backend still performs the final validation and authorization checks.

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
- `frontend/app/utils/qgchatErrors.ts` maps backend error codes to localized frontend messages.
- `frontend/app/utils/qgchatValidation.ts` contains shared frontend form validation helpers.
- Browser tokens are stored in `localStorage` under `qgchat.token`.
