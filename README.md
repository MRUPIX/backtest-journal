# Backtest Journal

Backtest Journal is a React and Spring Boot application with passwordless Telegram login. The browser starts a login request, the Telegram bot sends a single-use six-digit code, and successful verification creates a server-side session.

## Requirements

- Java 21
- Node.js 20.19+ or 22.12+
- A Telegram bot created with [BotFather](https://t.me/BotFather)

## Configure Telegram

1. Create a bot with BotFather and note its token and username.
2. Copy `backend/config/application-local.example.properties` to `backend/config/application-local.properties`.
3. Replace all placeholder values. Use the bot username without `@` and a random value of at least 32 characters for `auth.code-secret`.

`application-local.properties` is ignored by Git. Never commit the bot token. If the bot previously used a webhook, remove it before using this application's long polling.

The same settings can be supplied as `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`, and `AUTH_CODE_SECRET` environment variables.
When deploying behind a non-private reverse proxy, configure Tomcat's trusted proxy addresses or set `FORWARD_HEADERS_STRATEGY` to match that deployment so login rate limits use the client address.

## Run Locally

Start the API:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

Start the web app in another terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/auth` requests to the API at `http://localhost:8080`.

## Verify

```powershell
cd backend
.\mvnw.cmd clean test

cd ..\frontend
npm run lint
npm run build
```

Local development uses an in-memory H2 database, so users and login requests reset whenever the backend restarts. Configure `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `JPA_DDL_AUTO` for a persistent PostgreSQL environment.
