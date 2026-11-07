# Production Cleanup — Backup and Recovery Verification

**Environment:** Aiven PostgreSQL `bookbridge` @ `resourceswaphub-pahadiaarun-a976.a.aivencloud.com:20013`
**Prepared:** 2026-11-06
**Status:** logical snapshot taken and **restore-verified**; provider automated backup **not verifiable from this environment**.
**Production was not restored over, and no destructive statement was executed.**

---

## 1. Provider (Aiven) automated backups

| Check | Result |
| --- | --- |
| Aiven API token / console session available in this environment | **no** |
| Backup list, retention window, backup timestamps | **not retrievable** |
| Backup database identity / schema version | **not retrievable** |
| Provider backup restore test | **not performed** |

There is no Aiven API token, service account or console credential in the workspace
(`resourceswaphub-backend/.env` holds only the database DSN), so the Aiven backup entry, its
timestamp and its retention could not be inspected. This is reported as a **missing dependency**
rather than assumed to be fine — a provider reporting "backups enabled" is not evidence that a
restorable backup exists at the current point in time.

### What you must confirm before approving execution

In **Aiven Console → your PostgreSQL service → Backups**:

1. A backup exists with a timestamp **within the last 24 h and after the most recent deploy**
   (V11 was applied 2026-11-06 14:59:33 UTC).
2. The service's **point-in-time recovery (PITR)** window covers the planned cleanup window, if the
   plan is enabled on this service tier.
3. The backup is associated with service `resourceswaphub-pahadiaarun-a976` and database `bookbridge`.

If any of those is missing, do not approve execution until the backup state is known.

---

## 2. Independent logical snapshot (taken and verified here)

Because the provider backup could not be inspected, a full logical snapshot of every production
table was taken and then **restored into a separate local database to prove it is usable**.

| Property | Value |
| --- | --- |
| Snapshot timestamp | **2026-11-06T15:08:35Z** (2026-11-06 20:38 IST) |
| Location | `.prod-snapshot/` in the workspace (git-ignored, mode `700`) |
| Format | one UTF-8 TSV per table, header row, ISO-8601 timestamps, literal `\N` for NULL |
| Tables captured | **42 / 42** public tables |
| Rows captured | **1 816** |
| Integrity | SHA-256 per table, recorded in `.prod-snapshot/MANIFEST.tsv` |
| Schema version at capture | Flyway **V11** |
| Server | PostgreSQL 18.6 |
| Read mode | `SET TRANSACTION READ ONLY` — the dump cannot write |
| Size | 532 KB |

Manifest excerpt:

```
# taken_at_utc=2026-11-06T15:08:35.370395Z
# flyway={11}
# server=PostgreSQL 18.6
books	13	7e656233ea5feaf02fbfa8b7cbeee87005064dded2e534d49c36763a64b4bc04
…
conversation_participants	8	eb1d76ba9841a804ceec82c0124c58e0795eed5fba201854d90339e73a4e2c61
# total_rows=1816
```

### Restore verification performed

The snapshot was loaded into a **scratch schema on the local development PostgreSQL**
(`127.0.0.1:55432/bookbridge`, schema `prod_restore_check`) — created, populated, compared, dropped.
Production was never the target.

| Table | Expected | Restored | Match |
| --- | ---: | ---: | --- |
| books | 13 | 13 | OK |
| cities | 634 | 634 | OK |
| collection_items | 4 | 4 | OK |
| collections | 2 | 2 | OK |
| conversation_participants | 8 | 8 | OK |
| conversations | 4 | 4 | OK |
| countries | 2 | 2 | OK |
| districts | 791 | 791 | OK |
| flyway_schema_history | 11 | 11 | OK |
| institution_aliases | 46 | 46 | OK |
| institutions | 24 | 24 | OK |
| listings | 11 | 11 | OK |
| location_aliases | 104 | 104 | OK |
| messages | 8 | 8 | OK |
| refresh_tokens | 41 | 41 | OK |
| resource_categories | 11 | 11 | OK |
| states | 36 | 36 | OK |
| topic_aliases | 8 | 8 | OK |
| topics | 48 | 48 | OK |
| users | 10 | 10 | OK |
| *(21 further tables, all 0 rows)* | 0 | 0 | OK |

**Result: `RESTORE VERIFIED: every table row count matches the snapshot.` — 42/42 tables.**

### Re-snapshot before execution (done 2026-11-06T15:57:08Z)

A second snapshot was taken after the cleanup plan was approved and no production row had changed
(the per-table SHA-256 digests for `messages` and `conversation_participants` are identical to the
15:08:35Z capture). It was restored and verified again: **42/42 tables, 1 816 rows**.

| Snapshot | Taken (UTC) | Rows | Restore verified | Location |
| --- | --- | ---: | --- | --- |
| 1 (audit) | 2026-11-06T15:08:35Z | 1 816 | 42/42 | `.prod-snapshot-1508/` |
| 2 (pre-execution) | 2026-11-06T15:57:08Z | 1 816 | 42/42 | `.prod-snapshot/` |

A **third** snapshot must be taken immediately before the approved cleanup runs, because production
is live and accepts new rows at any time.

---

## 3. Recovery procedure

### A. Recover from the logical snapshot (works today, no provider dependency)

```bash
# 1. Point at the target database (never a live one while colleagues are writing)
export BOOKBRIDGE_DB_URL='jdbc:postgresql://<host>:<port>/<db>'
export BOOKBRIDGE_DB_USER='…'
export BOOKBRIDGE_DB_PASSWORD='…'

# 2. Bring the schema to the snapshot's version (V11)
cd resourceswaphub-backend && ./mvnw -q spring-boot:run   # Flyway migrates on boot; stop it after startup

# 3. Reload the data, parents first (the tool derives the FK order automatically)
java -cp resourceswaphub-database/.local/postgresql-42.7.14.jar:.prod-snapshot/classes \
     ProdSnapshot verify .prod-snapshot
```

`verify` recreates the tables in a scratch schema and loads the TSVs in foreign-key-safe order. For a
true restore into `public`, the same load order applies; the SQL is plain `INSERT` with typed casts,
so any client can replay it.

### B. Recover from an Aiven backup

1. Aiven Console → PostgreSQL service → **Backups** (or **PITR**), choose the point **before** the
   cleanup transaction.
2. Aiven restores into a **new** service; never overwrite the running one.
3. Verify on the restored service: `SELECT current_database()`, `SELECT max(string_to_array(version,'.')::int[]) FROM flyway_schema_history WHERE success` → must be `{11}`, and
   `SELECT count(*) FROM users` → 10, `listings` → 11, `messages` → 8.
4. Cut the application over by updating `BOOKBRIDGE_DB_URL` (configuration change — separately
   approved, not part of this cleanup).

### C. Cancel the cleanup itself

Every cleanup statement is inside one transaction. Before `COMMIT`, a failure or a count mismatch
aborts the whole thing. A partial state is impossible.

---

## 4. Recovery limitations

1. **Provider backup state is unverified** (see §1). The logical snapshot is the only *verified*
   recovery point as of 2026-11-06T15:08Z.
2. **The snapshot is not a full backup of `pg_dump` fidelity.** It preserves rows and column types
   but **not** indexes, constraints, triggers, sequences or grants; those are recreated by Flyway
   migrations V1–V11, which is why the recovery procedure runs the app first. Sequence values for
   `messages.id` (bigint identity) are re-seeded from the data by `setval` — the tool does not do
   this, so a real restore must run
   `SELECT setval(pg_get_serial_sequence('messages','id'), (SELECT max(id) FROM messages));` afterwards.
3. **Row counts of the snapshot are a point-in-time view.** Production is live and was deployed to
   during the audit (V11 at 14:59:33 UTC), so rows created after 15:08:35Z are *not* in the
   snapshot. Take a fresh snapshot immediately before executing the cleanup.
4. **No PITR granularity** is claimed: the snapshot granularity is "the moment the dump finished".
5. **The snapshot contains personal data** (e-mails, password hashes, refresh-token hashes). It is
   git-ignored and `chmod 700`; it must be deleted once the cleanup is verified and the retention
   window of the provider backup has passed.
6. **Storage objects are not covered** — the app stores uploads on the API host filesystem
   (`./storage`). There are zero `media_assets` / `listing_images` rows, so there is nothing to
   recover in that layer today. If real uploads appear before cleanup, back up that directory out of
   band.

---

## 5. Pre-execution checklist (to be re-run at cleanup time)

- [ ] `SELECT current_database(), current_user` → `bookbridge`, `avnadmin`
- [ ] `SELECT max(string_to_array(version,'.')::int[]) FROM flyway_schema_history WHERE success` → `{11}`
- [ ] `users` = 10, `listings` = 11, `conversations` = 4, `messages` = 8, `refresh_tokens` = 41
- [ ] Fresh `.prod-snapshot/` dump with a new timestamp and manifest
- [ ] `ProdSnapshot verify` → 42/42 OK
- [ ] Aiven backup/PITR point confirmed in console (owner action)
- [ ] No active deploy in progress
