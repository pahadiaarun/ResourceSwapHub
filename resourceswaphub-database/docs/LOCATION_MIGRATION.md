# Location migration

How the standardized directory (`V8`, `V9`) came to sit next to the free-text columns it replaces,
and how to run each step in the right order. Schema details: [LOCATION_SCHEMA.md](LOCATION_SCHEMA.md).
Data provenance and import formats: [LOCATION_DATA_SOURCES.md](LOCATION_DATA_SOURCES.md).

## Principles

1. **Additive only.** V8 creates the directory tables and adds nullable `country_id`, `state_id`,
   `city_id` to `users`, `listings` and `collections`. Nothing is dropped or rewritten, so the
   already-deployed frontend keeps working during and after the migration, and
   `jpa.hibernate.ddl-auto: validate` stays satisfiable. Previously applied migrations are never
   edited — new state arrives as a new version.
2. **No guessing.** A row is standardized only when its text maps to exactly one directory record.
   Anything ambiguous or unknown keeps its original text and NULL ids, which makes it a candidate
   again after a later import widens the directory.
3. **Reversible by the app, not by DDL.** Because every change is additive, rolling back means
   deploying the previous build; the columns it ignores are still there.

## Steps

### 1. Migrate the schema

Flyway applies `V8__normalized_locations.sql` then `V9__location_discovery_indexes.sql` at boot.
Both are safe on an empty or populated database: V8 seeds the two countries, all 28 states and 8
union territories with their ISO 3166-2:IN codes, and V9 adds indexes only.

**Prerequisite: the migrations must be inside the artifact.** The migration SQL lives in
`resourceswaphub-database/migrations/` and is copied to `db/migration` on the classpath by the
backend `pom.xml`. A container or jar built from a context that excludes that sibling directory
starts against an out-of-date schema and fails at Hibernate validation rather than migrating.
Verify before deploying: `unzip -l app.jar db/migration` must list V8 and V9.

### 2. Import or seed the directory

Cities and districts are never invented, so the level of detail depends on what has been imported.

```bash
# curated starter set (idempotent, only while the cities table is empty, source = CURATED_SEED)
java -jar app.jar
java -jar app.jar --bookbridge.location.seed=false     # to skip it

# authoritative dataset, operator-run
java -jar app.jar \
  --bookbridge.location.import-file=/data/lgd-districts.csv \
  --bookbridge.location.import-source=LGD \
  --bookbridge.location.import-source-url=https://lgdirectory.gov.in/ \
  --bookbridge.location.import-dataset-version=2024-03 \
  --bookbridge.location.import-verified=true
```

The importer is idempotent — it matches on `official_code`, then on parent scope plus normalized
name — so re-running refreshes rows instead of duplicating them, and a row without an official code
is stored with `official_code` left NULL.

### 3. Backfill existing rows

```bash
java -jar app.jar --bookbridge.location.backfill=true
```

`LocationBackfillRunner` is deliberately not an HTTP endpoint and never runs automatically: it
rewrites data, and it is only meaningful once the directory has content. The pass walks users, then
listings, then collections in id-controlled batches of 200 (a row that gets standardized drops out
of the candidate filter, so a cursor is used rather than an offset). For each row it calls the same
`LocationService.resolve(...)` the live write path uses, then stores the ids and mirrors the
directory's spelling back into the legacy text columns. The log ends with a summary — scanned and
matched per record type, plus up to 25 samples of rows it could not place:

```text
Location backfill complete: users=6/6 listings=0/0 collections=0/0 unmatched=0 (51 ms)
  not standardized: user <id> city='Nowhereville' state='Atlantis'
```

Re-runnable by design. Candidate selection covers both shapes that need attention:

- a row with city text but no `city_id` (never standardized), and
- a row whose ids are set while the mirrored text is missing or disagrees with the directory
  (standardized by an earlier pass that only wrote ids).

A row that still cannot be placed is reported, counted once per run and left untouched.

**Listings inherit their seller's state.** A bare city name can never identify a place, so a listing
or collection is resolved inside its owner's standardized state where one exists, and only falls
back to a directory-wide unique-name lookup when it does not. Users are therefore backfilled first.

### 4. Verify

```sql
-- every location-bearing row is either standardized or deliberately left as text
select count(*) filter (where city_id is not null) as standardized,
       count(*) filter (where city_id is null and nullif(city,'') is not null) as still_text
from users;

-- mirrored text never drifts from the directory row
select u.id, u.city, c.name
from users u join cities c on c.id = u.city_id
where u.city is distinct from c.name;      -- expect 0 rows
```

The same shape applies to `listings` and `collections`. `LocationIntegrationTest` asserts both
invariants plus the ambiguous-name cases end to end.

### 5. Later (not part of this migration)

Once every deployment has run the import and the backfill reports zero unmatched rows, the
`city`/`state`/`country` text columns become pure display cache and a future `V10` may read names
from the directory only, or drop the columns. That is intentionally deferred: doing it now would
break the currently deployed frontend and `ddl-auto: validate`.

## Known state of each database

| Database | Version | Notes |
| --- | --- | --- |
| local development (`bookbridge`, 127.0.0.1:55432) | V9 | directory seeded, backfill run, unmatched = 0 |
| test (`bookbridge_test`) | V9 | created by the test suite; the seed runner fills the directory |
| cloud (Aiven, used by the deployed app) | **V6** | V7–V9 have not been applied; the deployed container also lacks the migration files in its jar (step 1 prerequisite) |

Do not run the backfill against a database that has not reached V9, and do not treat the cloud
schema as current until the packaged migrations are fixed and applied.
