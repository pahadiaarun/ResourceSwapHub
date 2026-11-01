# Migration Guide

Flyway is the only supported mechanism for schema change. The API applies
migrations on startup and Hibernate validates the result with
`ddl-auto: validate`.

## Naming and ordering

```text
V<version>__<snake_case_description>.sql
```

Examples: `V5__listing_reports.sql`, `V6__reservation_holds.sql`.

- Versions are integers and never reused. Use `V5`, `V6`, …
- Never edit a migration that has run in any shared environment. Flyway stores a
  checksum and will refuse to start.
- Repeatable migrations (`R__…`) are not used for DDL.
- One logical change per migration; keep it reviewable.

## Writing a migration

1. Describe the change in the pull request and update the relevant docs in
   `database/docs/` (schema reference, dictionary, indexes, constraints).
2. Prefer additive changes:
   - add nullable columns, backfill, then add constraints;
   - create indexes concurrently on large production tables (outside a
     transaction, in a dedicated migration and maintenance window);
   - rename by adding the new column and dual-writing before dropping the old.
3. Always add primary keys, foreign keys with explicit `ON DELETE`, check
   constraints and comments for non-obvious rules.
4. Keep SQL portable to managed PostgreSQL: no extensions, no superuser
   operations, no `COPY … FROM PROGRAM`.
5. Never include credentials or environment-specific names.

Template:

```sql
-- V5: short purpose statement.
ALTER TABLE listings
    ADD COLUMN reserved_until TIMESTAMPTZ;

CREATE INDEX idx_listings_reserved
    ON listings (reserved_until)
    WHERE reserved_until IS NOT NULL;
```

## Testing against a fresh database

```bash
# rebuild the local cluster, then let Flyway migrate from V1
"./ResourceSwapHub database/scripts/local-postgres.sh" reset
"./ResourceSwapHub database/scripts/local-postgres.sh" start
cd "ResourceSwapHub Backend" && ./mvnw test
```

With Docker:

```bash
docker compose down -v
docker compose up -d postgres
cd "ResourceSwapHub Backend" && ./mvnw spring-boot:run
```

The integration tests run Flyway against `bookbridge_test`, so a broken
migration fails the build before deployment.

## Applying to an existing environment

1. Back up the database (see [BACKUP_RESTORE.md](BACKUP_RESTORE.md)).
2. Deploy the new API image; Flyway runs before the web server accepts traffic.
3. Watch startup logs for `Successfully applied N migration(s)`.
4. Verify `flyway_schema_history` and run the `/actuator/health` check.
5. Run the smoke queries in [QUERY_CATALOG.md](QUERY_CATALOG.md).

If startup fails after a migration, do **not** edit the migration in place.
Restore from backup, fix with a new forward migration, and redeploy.

## Destructive changes

Dropping a column or table requires a two-release window:

1. Release 1: stop reading/writing the column, keep it nullable.
2. Release 2: drop it in a dedicated migration after confirming no consumer.

Never run manual `DROP`/`TRUNCATE` against production. Document any important
DML (backfills, data repair) in a versioned migration so it is reproducible.

## Rollback policy

Flyway Community has no automatic undo. Recovery is restore-from-backup plus a
forward-fix migration. Every migration must therefore be small enough to reason
about and safe to re-run during a restore exercise.

## Checklist

- [ ] Filename follows the version convention and is unique.
- [ ] Runs on an empty database and on a copy of production data.
- [ ] No edits to previously applied files.
- [ ] Docs updated (schema, dictionary, indexes, constraints, queries).
- [ ] `./mvnw test` passes against fresh PostgreSQL.
- [ ] Backup taken before production apply.
