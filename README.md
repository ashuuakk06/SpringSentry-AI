# SpringSentry AI

SpringSentry AI is a student-built debugging assistant for Java and Spring Boot errors. It accepts a stack trace, returns a diagnosis and suggested next steps, saves analysis history, and can run tests for one bundled sample project.

**Developer:** Ashish Pagariya · **Level:** Third-year BTech project

## What works in this version

- React + TypeScript interface with account registration, login, dashboard, analysis form, results, profile, and history.
- Spring Boot REST API with input validation, BCrypt password hashes, JWT authentication, and per-user analysis history.
- BharatCode Chat Completions integration on the backend. The API key is read from `BHARATCODE_API_KEY`; it is never sent to the browser.
- Local demo analysis when no BharatCode key is configured. Demo results are labelled and are not presented as AI output.
- H2 file database for a zero-setup local run. PostgreSQL is available through Docker Compose.
- A fixed JUnit sample project that can be run from the dashboard. It does not execute submitted code or verify an AI suggestion.

## Requirements

- Java 21 or newer. The Maven compiler targets Java 21.
- Node.js 20 or newer and npm.
- Docker Desktop only if you choose PostgreSQL. The default local run does not require Docker.

## Run locally

Open two terminals from the extracted project folder.

**Terminal 1 — backend** (PowerShell):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

If your terminal is already inside `backend`, run `.\mvnw.cmd spring-boot:run` instead. On macOS/Linux, run `./mvnw spring-boot:run`.

The API listens on `http://localhost:8081`.

**Terminal 2 — frontend**:

```powershell
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, create an account, and submit a stack trace. The frontend proxies `/api` requests to port 8081. You can check the backend directly at `http://localhost:8081/api/health`.

The default database is stored under `backend/data/` and persists between restarts. It is excluded from the ZIP and Git. If no JWT secret is set, the backend generates a random development key each time it starts, so sign in again after a restart.

## Enable BharatCode analysis

Get a BharatCode API key from your BharatCode account. In PowerShell, run these commands in the backend terminal. `Get-Credential` prompts for the key in a password field, so it does not appear in the command history:

```powershell
cd backend
$credential = Get-Credential -UserName "BharatCode" -Message "Enter your BharatCode API key in the password field"
$env:BHARATCODE_API_KEY = $credential.GetNetworkCredential().Password
Remove-Variable credential
$env:BHARATCODE_MODEL = "qwen-3.8-27b"
.\mvnw.cmd spring-boot:run
```

The API key exists only in that backend process environment and is sent as a bearer token by the backend. Do not add it to React files, browser storage, screenshots, source control, or `.env` files. `.env.example` contains placeholders only; Spring does not automatically load it. Close the terminal to discard the environment variable. Set `BHARATCODE_MODEL` to another model available to your account if needed. The backend checks `GET /models` and uses the Chat Completions endpoint with connection and response timeouts. The frontend shows **AI Connected** only when BharatCode responds and the configured model is listed. With no key, it shows **Demo Mode** and uses the local demo analyzer. Provider errors do not silently fall back to demo output.

To check the live provider status, sign in to the app; the status badge calls the authenticated `GET /api/provider/status` backend endpoint. To verify an actual AI analysis, submit a harmless synthetic Java stack trace while the badge says **AI Connected**. A successful provider analysis is labeled **BharatCode AI** in the result and stored with source `BHARATCODE`. A connectivity check alone confirms the models endpoint and configured model, not that a chat completion succeeded. If the response is truncated or incomplete, the backend returns an error and does not save it as a completed diagnosis.

Set a strong `SPRINGSENTRY_JWT_SECRET` of at least 32 UTF-8 bytes before deployment.

## Use PostgreSQL (optional)

Start the bundled database:

```powershell
docker compose up -d postgres
```

Then, in the backend terminal, activate the PostgreSQL profile:

```powershell
$env:SPRING_PROFILES_ACTIVE = "postgres"
$env:SPRINGSENTRY_DB_PASSWORD = "springsentry-local-only"
.\mvnw.cmd spring-boot:run
```

The Compose file uses a local development password by default. Change it before sharing or deploying the service. Stop the database with `docker compose down`; its data remains in a named Docker volume. Use `docker compose down -v` only when you intentionally want to erase that database.

## Bundled sample verification

The dashboard’s **Run bundled sample tests** button runs the fixed Maven command against `sample-projects/calculator` and stops after 120 seconds. It reports the actual Maven test summary. It never accepts a user-supplied project path or runs the submitted source code. A passing sample suite does **not** prove that an AI-suggested fix resolves a submitted error; the analysis result remains `Not executed`.

## API overview

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Backend health check |
| `GET` | `/api/provider/status` | Check configured AI provider/model status (requires JWT) |
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Sign in and receive a JWT |
| `GET` | `/api/auth/me` | Get the signed-in account |
| `POST` | `/api/analyses` | Analyze and save a stack trace |
| `GET` | `/api/analyses` | List the signed-in user’s latest analyses |
| `GET` | `/api/analyses/{id}` | Read one of the signed-in user’s analyses |
| `POST` | `/api/verifications/sample` | Run the bundled calculator tests |

Except health and authentication routes, API requests require `Authorization: Bearer <token>`.

## Project layout

```text
backend/                 Spring Boot REST API and persistence
frontend/                React + TypeScript interface
sample-projects/         Allowlisted Maven sample for test execution
docker-compose.yml       Optional PostgreSQL service
docs/                    Project notes
```

## Current limits

- AI results are suggestions. They are not confirmed facts or verified fixes.
- Users review and apply suggested changes manually; SpringSentry does not modify source files.
- Verification runs only the bundled sample project. It does not run arbitrary submitted code.
- The default H2 database and local JWT secret are for development. Use PostgreSQL and a strong secret for a shared deployment.
- This is an educational MVP, not a production deployment or a complete hostile-code sandbox.
