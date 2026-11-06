# Production Test Data Audit — ResourceSwapHub

**Environment:** production (Aiven PostgreSQL)
**Audit date:** 2026-11-06 (UTC)
**Audit mode:** strictly read-only — every connection was opened with `SET TRANSACTION READ ONLY`
**Destructive operations performed:** none
**Status:** Phase 1 and Phase 2 complete. Awaiting explicit approval before cleanup.

---

## 1. Production environment verification

| Item | Value |
| --- | --- |
| Provider | Aiven for PostgreSQL |
| Hostname | `resourceswaphub-pahadiaarun-a976.a.aivencloud.com` |
| Port | `20013` |
| Database | `bookbridge` |
| Server address reported by PostgreSQL | `165.22.211.167`, server port `20013` |
| Database user | `avnadmin` |
| TLS | enabled (`sslmode=require`, `pg_stat_ssl.ssl = true`) |
| PostgreSQL version | 18.6 (x86_64-pc-linux-gnu) |
| Flyway schema version | **V11** (11 successful migrations, latest `phase1 supporting indexes`) |
| Connection config source | `resourceswaphub-backend/.env` → `BOOKBRIDGE_DB_URL` / `_USER` / `_PASSWORD` |
| Application version | `resourceswaphub-backend` at commit `66ed210` (`main`) |
| File / object storage | application filesystem (`BOOKBRIDGE_STORAGE_DIR=./storage`); **no S3/R2 bucket** configured |
| Audit tooling | `resourceswaphub-database/scripts/tools/ProdAudit.java` (read-only query runner), `resourceswaphub-database/scripts/tools/ProdSnapshot.java` (logical snapshot + restore verification) |

**Target confirmation.** The audit connected only to the Aiven host above, database `bookbridge`.
No local (`127.0.0.1:55432`), test (`bookbridge_test`), staging or prodlike database was queried for
this report. The tooling hard-fails if `BOOKBRIDGE_DB_URL` does not contain `aivencloud.com`.
Credentials were never printed, logged, or written into any file.

**Observation — production received a deployment during the audit window.** The migration
`V11__phase1_supporting_indexes.sql` was applied at `2026-11-06 14:59:33 UTC`, roughly one minute
after this audit began (`V10` had been applied at `14:42:48 UTC`). The database is live and being
deployed to; a fresh snapshot must be taken immediately before any approved cleanup executes.

### Soft-delete mechanisms

There is **no soft-delete anywhere in the schema**. No `deleted_at`, `is_deleted` or archive column
exists on any table. Removal is physical `DELETE`; the only non-destructive state is the domain
status columns (`listings.status = WITHDRAWN`, `resource_requests.status`, `moderation_state`).
Consequence: a deleted user or listing cannot be restored from application state — only from a
database backup (see `PRODUCTION_CLEANUP_BACKUP_AND_RECOVERY.md`).

### Audit and moderation records

`moderation_actions`, `resource_reports` and `listing_status_history` are **all empty (0 rows)**.
Nothing in production has ever been moderated or reported, so no audit record constrains the cleanup.

### Table inventory (exact counts)

| Table | Rows | | Table | Rows |
| --- | ---: | --- | --- | ---: |
| users | 10 | | listings | 11 |
| refresh_tokens | 41 | | listing_images | 0 |
| user_education | 0 | | listing_status_history | 0 |
| user_employment | 0 | | media_assets | 0 |
| user_interests | 0 | | collections | 2 |
| user_skills | 0 | | collection_items | 4 |
| user_subjects | 0 | | saved_resources | 0 |
| user_topics | 0 | | saved_collections | 0 |
| resource_requests | 0 | | resource_alerts | 0 |
| exchanges | 0 | | notifications | 0 |
| resource_reports | 0 | | moderation_actions | 0 |
| conversations | 4 | | conversation_participants | 8 |
| messages | 8 | | encryption_sessions | 0 |
| encryption_replay_cache | 0 | | institution_suggestions | 0 |
| books | 13 | | institutions | 24 |
| resource_categories | 11 | | institution_aliases | 46 |
| countries | 2 | | states | 36 |
| districts | 791 | | cities | 634 |
| location_aliases | 104 | | topics | 48 |
| topic_aliases | 8 | | skills / subjects | 0 / 0 |
| flyway_schema_history | 11 | | **total** | **1 816** |

Note: `user_profiles` does not exist; profile data is denormalised onto `users`. There is no
`institution records`-style table beyond `institutions` / `institution_aliases` /
`institution_suggestions`. `resource_exchanges` is `exchanges`.

---

## 2. Classification summary

| Table | Total | CONFIRMED_TEST | POTENTIAL_TEST | GENUINE_OR_UNCERTAIN |
| --- | ---: | ---: | ---: | ---: |
| users | 10 | 7 | 2 | 1 |
| refresh_tokens | 41 | 15 | 14 | 12 |
| listings | 11 | 11 | 0 | 0 |
| collections | 2 | 2 | 0 | 0 |
| collection_items | 4 | 4 | 0 | 0 |
| conversations | 4 | 3 | 1 (mixed) | 0 |
| conversation_participants | 8 | 7 | 1 (mixed) | 0 |
| messages | 8 | 7 | 1 (mixed) | 0 |
| books | 13 | 1 | 0 | 12 (reference catalogue) |
| all other user-content tables | 0 | 0 | 0 | 0 |

Nothing in `notifications`, `resource_alerts`, `saved_resources`, `saved_collections`,
`resource_requests`, `exchanges`, `resource_reports`, `moderation_actions`, `listing_status_history`,
`media_assets`, `listing_images`, `user_education`, `user_employment`, `user_interests`,
`user_skills`, `user_subjects`, `user_topics`, `institution_suggestions` or `encryption_sessions`
is test data — those tables are simply empty.

### Evidence standard used

A record was classified CONFIRMED_TEST only when at least one of these held:

1. Its primary key is one of the **fixed UUIDs hard-coded in `resourceswaphub-database/seeds/development/demo.sql`**
   (`a0000000-…` users, `c0000000-…` collections, `d0000000-…` listings, `e0000000-…` conversations),
   and its creation timestamp matches that seed's `now() - interval N days` arithmetic. The seed file
   itself is headed *"Development-only demo content. Never apply to production."*
2. Its e-mail is an automated-harness address with an embedded epoch
   (`e2e-<epoch>@bookbridge.dev`, `probe<epoch>@example.com`) or uses a reserved non-deliverable TLD
   (`.test`) with a self-describing display name ("E2E Tester", "Probe User", "UI Test Pass").
3. Its listing/collection content is explicitly labelled as such ("E2E Physics Test" by "E2E Author").

Records were **not** classified as test data for being old, inactive, owning no listings, having an
incomplete profile, or belonging to the founder. Two real-person accounts (below) are preserved on
exactly that basis.

---

## 3. Candidate records — users

| # | id | e-mail | Display name | Created (IST) | Class | Reason | Owns |
| --- | --- | --- | --- | --- | --- | --- | --- |
| U1 | `a0000000-0000-4000-8000-000000000001` | aisha@bookbridge.demo | Aisha Kapoor | 2026-09-22 14:42:21 | CONFIRMED_TEST | Hard-coded seed UUID; `.demo` domain; `now() - 40 days` matches seed | 4 listings, 1 collection, 2 conversations, 3 messages |
| U2 | `a0000000-0000-4000-8000-000000000002` | rohan@bookbridge.demo | Rohan Mehta | 2026-09-30 14:42:21 | CONFIRMED_TEST | Hard-coded seed UUID; `now() - 32 days` matches seed | 3 listings, 2 conversations, 3 messages |
| U3 | `a0000000-0000-4000-8000-000000000003` | meera@bookbridge.demo | Meera Iyer | 2026-10-07 14:42:21 | CONFIRMED_TEST | Hard-coded seed UUID; `now() - 25 days` matches seed | 3 listings, 1 collection, 1 conversation, 1 message |
| U4 | `1c69f63f-378e-4c2c-8e4f-8905c621f945` | e2e-1793524472@bookbridge.dev | E2E Tester | 2026-11-01 14:44:32 | CONFIRMED_TEST | Automated e2e harness address with epoch suffix; no human activity | nothing |
| U5 | `ae4bc4f3-52f6-480b-be7e-1c880387b26a` | e2e-1793524508@bookbridge.dev | E2E Tester | 2026-11-01 14:45:08 | CONFIRMED_TEST | Same harness, created 36 s after U4; owns synthetic listing | 1 listing, 2 conversations |
| U6 | `c56e4946-bd13-406b-a4f7-fd53d5b6307b` | probe1793539481@example.com | Probe User | 2026-11-01 18:54:41 | CONFIRMED_TEST | Scripted probe account (`probe<epoch>@example.com`), no content | nothing |
| U7 | `18c07ee3-63ac-4118-912c-d27d1fd7a909` | uitest.pass+nov2@resourceswaphub.test | UI Test Pass | 2026-11-02 00:24:31 | CONFIRMED_TEST | Reserved `.test` TLD (cannot deliver mail), self-describing test name, no content | nothing |
| U8 | `d400f3e2-0d53-4d46-a6d8-7d599994ae84` | pahadi\*\*\*@gmail.com | (founder) | 2026-11-01 19:21:40 | GENUINE_OR_UNCERTAIN | Real Google account (`google_subject` present), 12 rotated refresh tokens, no content | nothing |
| U9 | `0638b6b2-d574-426d-9fed-481068bba529` | pahadi\*\*\*1@gmail.com | (founder, second account) | 2026-11-01 19:25:32 | POTENTIAL_TEST | Visually a duplicate of U8 with a password login; **issued a refresh token at 2026-11-06 20:15 IST — actively used today**; holds 1 message in a mixed conversation | 1 message |
| U10 | `346f5cfb-c233-4596-bc93-b62010a3a0ad` | devend\*\*\*@gmail.com | Devender | 2026-11-06 07:56:03 | POTENTIAL_TEST | Plausible real Google address created today; may be a manual sign-up check, but nothing proves it. Preserve | nothing |

U9 and U10 are **not** proposed for deletion. U9 in particular shows live authentication activity
within the last hour of this audit, so deleting it would destroy a working login.

---

## 4. Candidate records — listings (all 11 are CONFIRMED_TEST)

| id | Owner | Book title | Price | Status | Created (IST) | In collections | Conversations | Exchanges | Saved |
| --- | --- | --- | --- | --- | --- | ---: | ---: | ---: | ---: |
| `d0000000-…-000000000001` | U1 Aisha | NCERT Mathematics | 250 FIXED | AVAILABLE | 2026-10-27 14:42:21 | 1 | 1 | 0 | 0 |
| `d0000000-…-000000000002` | U1 Aisha | NCERT Science | FREE | AVAILABLE | 2026-10-28 14:42:21 | 1 | 0 | 0 | 0 |
| `d0000000-…-000000000003` | U1 Aisha | First Flight: English | 120 NEG | AVAILABLE | 2026-10-29 14:42:21 | 0 | 0 | 0 | 0 |
| `d0000000-…-000000000004` | U2 Rohan | NCERT Social Science | FREE | AVAILABLE | 2026-10-30 14:42:21 | 0 | 0 | 0 | 0 |
| `d0000000-…-000000000005` | U2 Rohan | Sparsh Hindi | 150 FIXED | AVAILABLE | 2026-10-31 14:42:21 | 0 | 0 | 0 | 0 |
| `d0000000-…-000000000006` | U3 Meera | ICSE Concise Mathematics | NEG | AVAILABLE | 2026-10-26 14:42:21 | 1 | 0 | 0 | 0 |
| `d0000000-…-000000000007` | U3 Meera | ICSE Concise Physics | 180 FIXED | AVAILABLE | 2026-10-25 14:42:21 | 1 | 1 | 0 | 0 |
| `d0000000-…-000000000008` | U1 Aisha | NCERT Physics Part I | 300 FIXED | UNAVAILABLE | 2026-10-22 14:42:21 | 0 | 0 | 0 | 0 |
| `d0000000-…-000000000009` | U2 Rohan | NCERT Chemistry Part I | FREE | EXCHANGED | 2026-10-18 14:42:21 | 0 | 0 | 0 | 0 |
| `d0000000-…-000000000010` | U3 Meera | NCERT Biology | 200 NEG | WITHDRAWN | 2026-10-17 14:42:21 | 0 | 0 | 0 | 0 |
| `86b37ad4-983f-43a5-bda2-640b7620b1e2` | U5 e2e | **E2E Physics Test** (author "E2E Author") | FREE | AVAILABLE | 2026-11-01 14:45:08 | 0 | 2 | 0 | 0 |

Full IDs for the `d0000000` rows are `d0000000-0000-4000-8000-00000000000N`. Every one is a seed UUID
reproduced verbatim from `demo.sql`, and all ten carry the seed's canned notes
("This one has found a new home.", "Old but complete…"). No listing has `digital_media_id`,
`external_url`, `listing_images` or `media_assets` — there are no photos or uploaded files to clean.

`EXCHANGED` on `…000009` is seed state only: the `exchanges` table is empty, so no exchange record
would be destroyed.

---

## 5. Candidate records — collections, items, conversations, messages

**Collections (2, both CONFIRMED_TEST, both PRIVATE):**

| id | Owner | Title | Items |
| --- | --- | --- | ---: |
| `c0000000-0000-4000-8000-000000000001` | U1 | Class 10 CBSE essentials | 2 |
| `c0000000-0000-4000-8000-000000000002` | U3 | Class 9 ICSE favourites | 2 |

**Collection items (4):** `c…0001 → d…0001`, `c…0001 → d…0002`, `c…0002 → d…0006`, `c…0002 → d…0007`.
All four point at confirmed test listings owned by the same user who owns the collection.

**Conversations (4):**

| id | Participants | Listing | Messages | Class |
| --- | --- | --- | ---: | --- |
| `e0000000-0000-4000-8000-000000000001` | U1 Aisha, U2 Rohan | d…0001 | 3 | CONFIRMED_TEST |
| `e0000000-0000-4000-8000-000000000002` | U2 Rohan, U3 Meera | d…0007 | 2 | CONFIRMED_TEST |
| `425c738f-9afe-4e79-9f64-de4dfd89ce0c` | U1 Aisha, U5 e2e | 86b37ad4 | 2 | CONFIRMED_TEST — *both* participants are confirmed test accounts |
| `911bf3e8-a665-4dac-bccf-37587d220c4d` | U5 e2e, U9 founder-2 | 86b37ad4 | 1 | **POTENTIAL_TEST — mixed** |

**Messages (8):**

| id | Conversation | Sender | Body (truncated) | Class |
| ---: | --- | --- | --- | --- |
| 1 | e…0001 | U2 Rohan | "Hi Aisha! Is the Class 10 maths book still available?" | CONFIRMED_TEST |
| 2 | e…0001 | U1 Aisha | "Hi Rohan, yes it is! The pages are clean…" | CONFIRMED_TEST |
| 3 | e…0001 | U2 Rohan | "That sounds perfect. Would you be able to hold it…" | CONFIRMED_TEST |
| 4 | e…0002 | U2 Rohan | "Hi Meera, is the ICSE physics book negotiable?" | CONFIRMED_TEST |
| 5 | e…0002 | U3 Meera | "Yes, happy to talk about the price…" | CONFIRMED_TEST |
| 6 | 425c738f | U1 Aisha | "Is this still free?" | CONFIRMED_TEST |
| 7 | 425c738f | U1 Aisha | "nope" | CONFIRMED_TEST |
| 8 | 911bf3e8 | U9 founder-2 | "Hi" | **POTENTIAL_TEST — mixed** |

Conversation `911bf3e8` is the **only mixed object in production**: a confirmed test account (U5) on
one side and the founder's actively-used second account (U9) on the other, carrying a single "Hi".
It is the sole reason the cleanup cannot be a single uniform operation.

---

## 6. Candidate records — catalogue

| id | Title | Author | Used by listings | Class |
| --- | --- | --- | ---: | --- |
| `b0000000-0000-4000-8000-00000000000{1..10}` | NCERT/ICSE textbooks | (real authors) | 10 | GENUINE_OR_UNCERTAIN — reference catalogue from `seeds/reference/catalogue.sql`; no user data, safe to keep and reused by future real listings |
| `b0000000-0000-4000-8000-000000000011` | New Oxford Modern English | David Horsburgh | 0 | GENUINE_OR_UNCERTAIN — reference catalogue, unreferenced |
| `b0000000-0000-4000-8000-000000000012` | Cambridge IGCSE Biology | Mary Jones | 0 | GENUINE_OR_UNCERTAIN — reference catalogue, unreferenced |
| `95e946a4-5cec-40ca-98aa-f5760641f0d5` | **E2E Physics Test** | **E2E Author** | 1 (the e2e listing) | CONFIRMED_TEST |

Only the `E2E Physics Test` catalogue row is synthetic. The twelve `b0000000` rows are neutral
reference data with no link to any person; deleting them would buy nothing and would break the
`catalogue.sql` contract the seed relies on. **Recommendation: preserve all `b0000000` rows.**

---

## 7. File and storage audit

| Check | Result |
| --- | --- |
| `media_assets` rows | 0 |
| `listing_images` rows | 0 |
| `listings.digital_media_id` non-null | 0 |
| `listings.external_url` non-null | 0 |
| `collections.cover_media_id` non-null | 0 |
| Storage backend | local filesystem (`./storage`) on the API host — no bucket to enumerate |

**No storage objects are associated with any test record, and no orphaned files can be attributed to
this application.** Phase 7 therefore has an empty work list. A filesystem sweep on the API host is
out of scope here (it is not reachable from this environment and there is nothing in the database
pointing at it).

---

## 8. Relationship graph for the confirmed test set

```
users U1 U2 U3 U4 U5 U6 U7
 ├── refresh_tokens           15   (U1 8, U4 2, U5 1, U6 3, U7 1)
 ├── listings                 11   (U1 4, U2 3, U3 3, U5 1)
 │    ├── listing_images       0
 │    ├── listing_status_history 0
 │    ├── exchanges             0
 │    ├── resource_reports      0
 │    ├── saved_resources       0
 │    └── collections via collection_items 4
 ├── collections               2
 ├── conversation_participants 7   (U1 2, U2 2, U3 1, U5 2)
 │    └── conversations        4   (3 fully test, 1 mixed)
 │         └── messages        8   (7 test, 1 mixed)
 ├── messages                  7
 ├── notifications             0
 ├── resource_alerts           0
 ├── saved_resources/collections 0
 ├── resource_requests         0
 ├── media_assets              0
 ├── user_education/employment/interests/skills/subjects/topics 0
 ├── institution_suggestions   0
 ├── resource_reports/moderation_actions 0
 └── encryption_sessions       0
```

### Foreign-key rules that shape the cleanup

| FK | ON DELETE | Consequence for this cleanup |
| --- | --- | --- |
| `listings.owner_id → users` | CASCADE | Deleting a test user silently deletes their listings |
| `collections.owner_id → users` | CASCADE | …and their collections (and `collection_items` via its own CASCADE) |
| `messages.sender_id → users` | CASCADE | …and every message they sent, in *any* conversation |
| `conversation_participants.user_id → users` | CASCADE | …and their participant row, **including in the mixed conversation** |
| `conversations.listing_id → listings` | **SET NULL** | Deleting a listing does *not* delete its conversations — the mixed conversation survives as a NULL-listing thread |
| `listing_images.media_id → media_assets` | RESTRICT | no-op here (both empty) |
| `listings.book_id → books` | RESTRICT | the `E2E Physics Test` book must be deleted *after* its listing |
| `moderation_actions.actor_id`, `resource_reports.reviewed_by`, `listing_status_history.changed_by` | SET NULL | no-op here (all empty) |
| `institution_suggestions.suggested_by → users` | **NO ACTION** | would block user deletion if any existed; table is empty |

Because of the CASCADE web, the cleanup plan deletes **explicit leaf rows first** and only then the
user rows, so that no delete depends on an invisible cascade to reach the right set of records.

---

## 9. Expected deletion impact (confirmed set only)

| Table | Rows to delete | Remaining | Notes |
| --- | ---: | ---: | --- |
| messages | 7 | 1 | message id 8 preserved (mixed conversation) |
| conversation_participants | 7 | 1 | test-side row of the mixed conversation is removed because its user is a test account |
| conversations | 3 | 1 | mixed conversation preserved |
| collection_items | 4 | 0 | all four point at test listings |
| collections | 2 | 0 | |
| listings | 11 | 0 | |
| refresh_tokens | 15 | 26 | founder 12 + 13 + 1 preserved |
| users | 7 | 3 | 3 real-person accounts preserved |
| books | 1 | 12 | only `E2E Physics Test` |
| **total** | **56** | | out of 1 816 rows |

Side effect to be aware of: after the listing `86b37ad4…` is deleted, the preserved mixed
conversation `911bf3e8…` becomes a thread with `listing_id = NULL` and a single participant (U9) with
one message. That is a harmless but slightly odd row. Deleting it is a *separate* decision requiring
its own approval (see the cleanup plan's optional block).

Master data — `institutions` (24), `institution_aliases` (46), `resource_categories` (11),
`countries` (2), `states` (36), `districts` (791), `cities` (634), `location_aliases` (104),
`topics` (48), `topic_aliases` (8) — is entirely untouched. No test user references any of it, and
none of it will be deleted even though test users exist.

---

## 10. Preserved records (explicit)

| Record | Why preserved |
| --- | --- |
| `d400f3e2…` founder Google account | Real authenticated Google identity; no test evidence |
| `0638b6b2…` founder second account (POTENTIAL_TEST) | Actively authenticating today; deleting would break a live login |
| `346f5cfb…` Devender account (POTENTIAL_TEST) | Plausibly a real person's sign-up; unproven either way |
| All 26 refresh tokens of those three accounts | Belong to preserved identities |
| Conversation `911bf3e8…` (mixed) | Contains a preserved participant; needs separate approval |
| Message id 8 | Authored by a preserved account |
| 12 `b0000000…` catalogue books | Neutral reference data reused by future listings |
| All geographic / institution / category / topic master data | Shared reference data |

---

## 11. Unresolved ambiguities

1. **U9 (`…1@gmail.com`) is classed POTENTIAL_TEST but shows today's login activity.** It may be the
   founder's working app account rather than a test artefact. Default action: preserve.
2. **U10 (`devender…@gmail.com`) created 2026-11-06 07:56 IST** with an e-mail/password registration.
   It could be a deliberate smoke-test of production sign-up. No evidence either way. Default: preserve.
3. **Provider-level backup could not be verified from this environment** (no Aiven API token or
   console session). See `PRODUCTION_CLEANUP_BACKUP_AND_RECOVERY.md` — this is mitigated with a
   verified logical snapshot, but the Aiven automated-backup entry must be confirmed by you.
4. **`demo.sql` says "Never apply to production"** yet its rows are in production. The seed file is
   currently *modified* in the working tree by other in-progress work; the UUIDs above match the
   committed version.
5. **Production is being deployed to during working hours** (V11 arrived mid-audit). Cleanup should
   run in a quiet window with a fresh snapshot taken minutes beforehand.

---

## 12. Audit provenance

| Step | Command |
| --- | --- |
| Identity / read-only proof | `java -cp postgresql-42.7.14.jar resourceswaphub-database/scripts/tools/ProdAudit.java identity` |
| Table inventory | `… ProdAudit.java tables` |
| Exact counts | `… ProdAudit.java file /tmp/counts.sql` |
| FK graph, triggers | `… ProdAudit.java schema` |
| Logical snapshot | `… ProdSnapshot.java dump .prod-snapshot` (1 816 rows, SHA-256 per table) |
| Restore verification | `… ProdSnapshot.java verify .prod-snapshot` → 42/42 tables match, local scratch schema |

The snapshot lives in `.prod-snapshot/` (git-ignored, `chmod`-restricted to the local user) and
contains production rows including refresh-token hashes — it must not be committed or shared.
