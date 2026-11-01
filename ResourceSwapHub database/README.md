# ResourceSwapHub Database

PostgreSQL is the single source of truth for all ResourceSwapHub data. Flyway owns the
schema: the files under [`migrations/`](migrations) are the only supported way to
change DDL, and the application validates the resulting schema on every boot.

## Layout

```text
ResourceSwapHub database/
├── README.md
├── docs/                 # design and operations documentation
├── migrations/           # Flyway versioned migrations (V1 … V6)
├── seeds/
│   ├── reference/        # canonical catalogue data, safe for any environment
│   └── development/      # demo users, listings and chats, development only
└── scripts/
    ├── local-postgres.sh # zero-Docker PostgreSQL 17 for development
    └── DatabaseTool.java # JDBC helper for split SQL files and bootstrap
```

## Local setup

### Option A — Docker Compose (recommended)

From the repository root:

```bash
docker compose up -d postgres
```

Compose creates the `bookbridge` database and runs the API container against it.
Flyway migrates automatically when the API starts.

### Option B — project-local PostgreSQL

The script downloads PostgreSQL 17 binaries to `ResourceSwapHub database/.local`
and keeps its data directory there, so no Docker daemon is required.

```bash
"./ResourceSwapHub database/scripts/local-postgres.sh" start   # initdb + start on 127.0.0.1:55432
cd "ResourceSwapHub Backend" && ./mvnw spring-boot:run         # Flyway applies migrations
cd .. && "./ResourceSwapHub database/scripts/local-postgres.sh" seed all
```

`start` bootstraps two databases, `bookbridge` and `bookbridge_test`, with the
`bookbridge` role. Stop the server with `stop`; `reset` deletes the data directory
so the next `start` recreates it. Ports and credentials can be overridden with the
`BOOKBRIDGE_DB_*` environment variables.

Convenience targets in the root `Makefile` wrap the same commands.

### Connecting with pgAdmin (or any SQL client)

The local instance does **not** use the PostgreSQL default port 5432 and only
listens on loopback, so point your client at:

| Field | Value |
| --- | --- |
| Host | `127.0.0.1` |
| Port | `55432` |
| Database | `bookbridge` |
| Username | `bookbridge` |
| Password | `bookbridge_dev` |

A connection targeting `localhost:5432` fails by design — nothing else owns port
5432 on this machine. To change the port or credentials, set `BOOKBRIDGE_DB_PORT`,
`BOOKBRIDGE_DB_USER` or `BOOKBRIDGE_DB_PASSWORD` before running `start` (and
update `"ResourceSwapHub Backend"/.env` to match).

## Migrations

| Version | File | Purpose |
| --- | --- | --- |
| V1 | `V1__identity_and_media.sql` | users, refresh tokens, media assets |
| V2 | `V2__catalog_and_listings.sql` | books, collections, listings, listing images |
| V3 | `V3__communication.sql` | conversations, participants, messages |
| V4 | `V4__search_and_performance_indexes.sql` | FTS and discovery indexes |

Rules:

- Never edit an applied migration. Add a new versioned file instead.
- Migrations must be portable to Aiven/any managed PostgreSQL: no extensions and
  no superuser-only features.
- Every new table needs primary keys, foreign keys, check constraints and the
  indexes its queries require.
- Apply migrations by starting the API (or by running Maven tests, which migrate
  `bookbridge_test`). Do not run DDL by hand in shared environments.

See [`docs/MIGRATION_GUIDE.md`](docs/MIGRATION_GUIDE.md) before writing one.

## Seeds

```bash
"./ResourceSwapHub database/scripts/local-postgres.sh" seed reference  # canonical books only
"./ResourceSwapHub database/scripts/local-postgres.sh" seed all        # books + demo community
```

- `reference/catalogue.sql` is idempotent and contains no personal data. It is
  safe to use as a baseline in staging. It inserts NCERT/ICSE textbook editions
  and resolves conflicts on the generated dedupe key.
- `development/demo.sql` creates three Pune/Bengaluru readers, ten listings, two
  collections and two conversations. Demo password: `BookBridge#2026`.
  Never apply it to production.
- The development seed resolves books by title and class instead of hard-coded
  UUIDs, so it still works when the catalogue was populated through the API.

## Testing migrations against a fresh database

```bash
"./ResourceSwapHub database/scripts/local-postgres.sh" reset
"./ResourceSwapHub database/scripts/local-postgres.sh" start
cd "ResourceSwapHub Backend" && ./mvnw test          # Flyway migrates bookbridge_test from V1
```

The integration suite runs against real PostgreSQL, validates every Hibernate
mapping with `ddl-auto: validate`, and truncates data between tests.

## Documentation

- [ER diagram](docs/ER_DIAGRAM.md)
- [Schema reference](docs/SCHEMA_REFERENCE.md)
- [Table relationships](docs/TABLE_RELATIONSHIPS.md)
- [Data dictionary](docs/DATA_DICTIONARY.md)
- [Indexing strategy](docs/INDEXING_STRATEGY.md)
- [Business constraints](docs/BUSINESS_CONSTRAINTS.md)
- [Query catalog](docs/QUERY_CATALOG.md)
- [Migration guide](docs/MIGRATION_GUIDE.md)
- [Backup and restore](docs/BACKUP_RESTORE.md)
