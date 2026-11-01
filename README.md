# ResourceSwapHub

ResourceSwapHub is an educational resource-sharing platform: people list school
and college books and study materials they own — free, fixed-price or
negotiable — and students discover them by class, education board, subject,
condition and location, then message the owner in real time to arrange a
pickup.

The stack is a **Next.js 16 + React + TypeScript + Tailwind frontend**, a
**Spring Boot (Java 21) modular-monolith API**, **PostgreSQL with Flyway
migrations**, and **STOMP over WebSocket** for chat. Docker Compose wires it all
together locally; GitHub Actions runs the CI gates.

```
ResourceSwapHub/
├── resourceswaphub-backend/    # Spring Boot API (REST + WebSocket), Java 21
├── resourceswaphub-frontend/   # Next.js App Router web client, installable PWA
├── resourceswaphub-database/   # Flyway migrations, seeds, scripts and schema documentation
├── docker-compose.yml
├── Makefile
└── .github/workflows/ci.yml
```

## Quick start

### Option A — Docker Compose (one command)

```bash
docker compose up -d
```

Then open:

- Web app — http://localhost:3000
- API — http://localhost:8080/api/v1
- Swagger UI — http://localhost:8080/swagger-ui.html
- Health — http://localhost:8080/actuator/health

The API container migrates the database with Flyway on boot. Database user and
credentials in Compose match the application defaults (`bookbridge` /
`bookbridge_dev`) and the database is published on host port **55432** so the
`resourceswaphub-database/scripts` tools work unchanged.

### Option B — no Docker

1. **PostgreSQL 17** (no Docker daemon required):

   ```bash
   make db                 # or ./resourceswaphub-database/scripts/local-postgres.sh start
   ```

2. **API** (Flyway migrates automatically at boot):

   ```bash
   make api                # cd resourceswaphub-backend && ./mvnw spring-boot:run
   ```

3. **Web**:

   ```bash
   make web                # cd resourceswaphub-frontend && npm install && npm run dev
   ```

4. Optional demo community (3 users, 10 listings, 2 collections, 2 chats):

   ```bash
   make db-seed            # ./resourceswaphub-database/scripts/local-postgres.sh seed all
   ```

### Demo accounts (after seeding)

| Email                        | Password          | Persona          |
| ---------------------------- | ----------------- | ---------------- |
| `aisha@bookbridge.demo`      | `BookBridge#2026` | Aisha Kapoor     |
| `rohan@bookbridge.demo`      | `BookBridge#2026` | Rohan Mehta      |
| `meera@bookbridge.demo`      | `BookBridge#2026` | Meera Iyer       |

Sign up any real account as normal — the demo data is development-only seed.

## Feature tour

- **Auth & profile** — register/login with JWT access tokens and rotating
  refresh tokens stored in an HttpOnly cookie; optional Google Sign-In
  (`GOOGLE_CLIENT_ID`); profile with name, avatar, city and locality.
- **Catalogue** — deduplicated canonical resource editions (title, author,
  publisher, ISBN, class, board, subject, edition, language). Listings reuse
  catalogue records instead of duplicating them.
- **Listings & collections** — photos (filesystem-backed `/media` uploads),
  condition, `FREE` / `FIXED_PRICE` / `NEGOTIABLE` pricing, city + locality,
  statuses `AVAILABLE` / `UNAVAILABLE` / `WITHDRAWN` / `EXCHANGED`.
- **Discovery** — full-text title search plus subject/class/board/price
  type/condition/city filters, sorted by recency, paginated, with loading,
  empty and error states. Public (read-only) without sign-in.
- **Direct chat** — the core loop. Conversation is keyed to a listing, messages
  are persisted in PostgreSQL and delivered over STOMP WebSocket, unread counts
  are tracked per participant, and only the two participants can access the
  thread (verified by tests, including outsider rejection).
- **PWA** — web manifest, icons, service worker with app-shell caching and an
  `/offline` fallback.

## Configuration

Both apps are configured through environment variables (see complete annotated
examples in [`resourceswaphub-backend/.env.example`](resourceswaphub-backend/.env.example) and
[`resourceswaphub-frontend/.env.example`](resourceswaphub-frontend/.env.example)). Credentials are never
hardcoded; JWT secrets must be provided in any real deployment.

Backend variables use the `BOOKBRIDGE_` prefix (a deliberate internal
identifier kept for compatibility — see `.env.example`): `BOOKBRIDGE_DB_URL`,
`BOOKBRIDGE_DB_USER`, `BOOKBRIDGE_DB_PASSWORD`, `BOOKBRIDGE_JWT_SECRET`,
`BOOKBRIDGE_COOKIE_SECURE`, `BOOKBRIDGE_CORS_ORIGINS`,
`BOOKBRIDGE_FRONTEND_ORIGIN`, `BOOKBRIDGE_STORAGE_DIR`, `GOOGLE_CLIENT_ID`.
Frontend variables: `BACKEND_URL`, `NEXT_PUBLIC_WS_URL` and
`NEXT_PUBLIC_SITE_URL` (canonical/OpenGraph origin), plus
`NEXT_PUBLIC_GOOGLE_CLIENT_ID`.

### Google sign-in

The whole flow is implemented end-to-end (Google Identity Services button in the
frontend, ID-token verification against Google's JWKS in the API, automatic
account linking by e-mail). It stays dormant until both sides of the app know
an OAuth client ID:

1. In [Google Cloud Console](https://console.cloud.google.com/) → **APIs &
   Services → Credentials**, create an **OAuth client ID** of type *Web
   application*. Add authorized JavaScript origins: `http://localhost:3000`
   (development) and, later, `https://resourceswaphub.work.gd`. No redirect
   URI is needed — the app does not use OAuth redirects.
2. Set the client ID (it is public; safe to commit as an example but normally
   passed as env) on both sides and restart both processes:
   - Backend: `GOOGLE_CLIENT_ID=…` (exposes `/auth/providers` as
     `{ "googleEnabled": true }`)
   - Frontend: `NEXT_PUBLIC_GOOGLE_CLIENT_ID=…` (renders the "Continue with
     Google" button on the login/register pages)
3. The button appears immediately for existing users too: a Google account
   whose e-mail already exists links to that account on first sign-in instead
   of creating a duplicate.

Without a client ID nothing breaks — the button simply does not render.

## Deploying to resourceswaphub.work.gd

The domain is planned but **not hardcoded anywhere** — every public URL is
environment-driven. No code change is required to switch; set the variables and
complete the manual steps below.

1. **DNS** — point `resourceswaphub.work.gd` (and `www.` if desired) at your
   host: an `A`/`AAAA` or `CNAME` record to the platform serving the Next.js
   app. The API can stay behind the same domain (`…/api/v1` is proxied by
   Next.js) or on a subdomain of its own.
2. **TLS** — provision a certificate (the free-tier certification of most
   hosts, e.g. Cloudflare/Caddy/Let's Encrypt, works). `work.gd` subdomains use
   standard DNS; nothing special is required.
3. **Backend** — set on the API container/process:
   - `BOOKBRIDGE_COOKIE_SECURE=true`
   - `BOOKBRIDGE_CORS_ORIGINS=https://resourceswaphub.work.gd`
   - `BOOKBRIDGE_FRONTEND_ORIGIN=https://resourceswaphub.work.gd`
   - a generated `BOOKBRIDGE_JWT_SECRET` (`openssl rand -base64 48`)
4. **Frontend** — set:
   - `NEXT_PUBLIC_SITE_URL=https://resourceswaphub.work.gd`
   - `NEXT_PUBLIC_WS_URL=wss://resourceswaphub.work.gd/ws` only if the API is
     served on a separate public origin; under the same domain the dev default
     (which appends port 8080) does not apply — see the `wsUrl()` helper in
     `resourceswaphub-frontend/components/inbox-workspace.tsx`.
   - `BACKEND_URL=http://api:8080` (container-internal) or the API's public URL.
5. **OAuth (optional Google sign-in)** — in your Google OAuth client add the
   authorized JavaScript origin `https://resourceswaphub.work.gd` and set the
   same `GOOGLE_CLIENT_ID` on the backend. The app performs sign-in via GIS
   token verification; there are no OAuth redirect callbacks to configure.
6. **Cookies** — refresh cookies are `HttpOnly` + `SameSite=Lax`; with
   `BOOKBRIDGE_COOKIE_SECURE=true` they are also `Secure`. No cookie domain is
   set (host-only cookies), which keeps auth scoped to the app's own origin.
7. **Email/password-reset URLs** — not applicable in this version: password
   reset and email verification flows are not implemented yet.

## Database

Flyway is the source of truth for DDL (`ResourceSwapHub
database/migrations/V1…V6`). Every table
is documented in [`resourceswaphub-database/docs/`](resourceswaphub-database/docs) — ER diagram, schema
reference, data dictionary, indexing strategy, business constraints, query
catalog, migration guide and backup/restore runbook. The database README
explains how to run migrations against a fresh server and how to use the seed
data.

Hard rules: never hand-edit a migrated schema, never apply DDL manually in
shared environments, add a new versioned migration instead, and keep
migrations portable (no extensions, no superuser-only statements) so the schema
works on Aiven or any managed PostgreSQL.

## Testing & CI

```bash
make test          # backend: Spring Boot tests against real PostgreSQL
cd resourceswaphub-frontend && npm run lint && npm run build
```

The backend suite runs integration tests over real HTTP + STOMP (auth flows,
listing management, discovery, chat delivery, unread counts, outsider
rejection) with `ddl-auto: validate`, ensuring Hibernate mappings always match
the Flyway schema. GitHub Actions (`.github/workflows/ci.yml`) runs the backend
suite, the frontend lint/build, and both Docker image builds on every push and
pull request.

## Ports and services (local)

| Service     | Port  | Notes                                        |
| ----------- | ----- | -------------------------------------------- |
| PostgreSQL  | 55432 | `127.0.0.1:55432`, both `bookbridge` (main) and `bookbridge_test` |
| API         | 8080  | REST under `/api/v1`, WS at `/ws`, Swagger UI |
| Web         | 3000  | Next.js; `/api/v1/*` is proxied to the API    |

Stop the stack with `docker compose down` (add `-v` to drop the database
volume) or `make db-stop` for the script-managed Postgres.

## Google Sign-In & Extended Profiles

### Google OAuth Setup
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project and enable Google Identity Services
3. Create OAuth 2.0 Client ID (Web application)
4. Add authorized JavaScript origins: `http://localhost:3000` (dev), your production frontend URL
5. Set environment variable: `GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com`
6. In backend: set `bookbridge.google-client-id` or env var `GOOGLE_CLIENT_ID`

### Extended Profile Features
- Optional role selection (School Student, College Student, Working Professional, Teacher, Other)
- Conditional profile sections based on role
- Institution directory with search (UDISE+/AISHE data importable)
- Multiple education/employment records support (schema ready)
- Privacy controls
- All additional fields are optional

### Institution Data
Sources investigated:
- UDISE+: https://udiseplus.gov.in/ (bulk data may require permission)
- AISHE: https://aishe.gov.in/ (Higher Education Directory available)
- AICTE: https://www.aicte-india.org/

Import process: Use the `institutions` table via SQL/script. Maintain source, source_url, verification_status, and official codes.

## Request Encryption (AES-256-GCM)
Additional application-layer encryption for sensitive API requests.
- Protocol: ECDH P-256 key exchange, HKDF-SHA-256, AES-256-GCM
- Frontend: Web Crypto API in centralized API client
- Backend: Decryption filter before request processing
- See docs/security/ for protocol details, threat model, key establishment
- Works alongside HTTPS/TLS as defense-in-depth
