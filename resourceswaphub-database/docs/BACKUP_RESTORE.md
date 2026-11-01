# Backup and Restore

ResourceSwapHub runs on plain PostgreSQL, so standard `pg_dump`/`pg_restore` and
provider snapshots cover every scenario. Aiven (and most managed providers)
offer automated daily backups with point-in-time recovery; use those for
production and keep the commands below for verification, local work and
provider migration.

## What to back up

| Data | Location | Method |
| --- | --- | --- |
| Relational data and schema | PostgreSQL database | `pg_dump` / provider snapshot |
| Uploaded images | `BOOKBRIDGE_STORAGE_DIR` (default `./storage`) or object storage | filesystem/object copy |
| Secrets and config | environment variables / secret manager | provider secret backup, never in Git |

The database and the media directory form one logical backup. Restoring one
without the other leaves broken image references.

## Logical backup

Custom format (recommended, compressed and selective restore):

```bash
pg_dump --format=custom --no-owner --no-privileges \
  --file=bookbridge-$(date +%Y%m%d-%H%M).dump \
  "$BOOKBRIDGE_DB_URL"
```

Plain SQL for inspection:

```bash
pg_dump --format=plain --no-owner --no-privileges \
  "$BOOKBRIDGE_DB_URL" > bookbridge.sql
```

Media directory:

```bash
tar -czf bookbridge-media-$(date +%Y%m%d).tar.gz storage/
```

Verify a dump is readable before shipping it off-host:

```bash
pg_restore --list bookbridge-*.dump | head
```

## Restore into a fresh database

```bash
createdb bookbridge_restore
pg_restore --no-owner --no-privileges --dbname=bookbridge_restore bookbridge.dump
psql bookbridge_restore -c "SELECT count(*) FROM listings;"
```

Then point the API at the restored database:

```bash
BOOKBRIDGE_DB_URL=jdbc:postgresql://host:5432/bookbridge_restore ./mvnw spring-boot:run
```

Flyway validates that the schema matches the application version. If the dump is
older than the code, start the API and let Flyway apply the missing forward
migrations. If it is newer, deploy the matching application version instead.

## Restore using the local cluster

The project-local cluster has no `psql`/`pg_dump` binaries, so use Docker or a
system PostgreSQL client against port `55432`:

```bash
docker run --rm -i --network host postgres:17 \
  pg_restore --no-owner --no-privileges \
  --dbname=postgresql://bookbridge:bookbridge_dev@127.0.0.1:55432/bookbridge < backup.dump
```

## Point-in-time recovery (managed providers)

1. Open the provider console and choose the timestamp just before the incident.
2. Restore to a new service/branch; never overwrite the primary in place.
3. Validate row counts and run the smoke queries from `QUERY_CATALOG.md`.
4. Switch `BOOKBRIDGE_DB_URL` to the recovered instance and restart the API.
5. Keep the old instance until the new one has served traffic successfully.

## Retention and drills

- Keep at least 7 daily, 4 weekly and 3 monthly backups.
- Store copies in a second region/bucket.
- Encrypt backups at rest; restrict access to the operator role.
- Run a restore drill every quarter and record the time to recover (target < 1h).
- Test that media files and the database restore to a consistent point.

## Emergency checklist

- [ ] Stop writes (scale API to zero or enable maintenance mode).
- [ ] Take a safety snapshot of the damaged database before touching it.
- [ ] Restore the most recent good backup to a new instance.
- [ ] Verify schema version and data counts.
- [ ] Repoint the application and restart.
- [ ] Confirm sign-in, discovery, listing detail and chat with a smoke test.
- [ ] Write an incident note with the recovery timeline and follow-ups.
