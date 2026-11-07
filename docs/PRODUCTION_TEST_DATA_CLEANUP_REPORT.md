# Production Test Data Cleanup — Final Report

**Target:** Aiven PostgreSQL `bookbridge` @ `resourceswaphub-pahadiaarun-a976.a.aivencloud.com:20013`
**Executed:** 2026-11-06, ~16:2x UTC
**Approved variant:** option A (58→60 rows) — approved after the original plan hit a CHECK-constraint blocker
**Outcome:** **COMMITTED — 60 rows deleted, every table delta matched the approved plan**
**Schema:** Flyway `{11}`, 11 successful migrations, **unchanged**

---

## 1. What was executed

| Step | Table | Rows | Selection |
| ---: | --- | ---: | --- |
| 1 | `messages` | 7 | ids 1–7 |
| 2 | `conversation_participants` | 7 | `user_id` of the 7 test accounts |
| 3 | `conversations` | 3 | `e…0001`, `e…0002`, `425c738f…` |
| 4 | `collection_items` | 4 | the 2 test collections |
| 5 | `collections` | 2 | `c…0001`, `c…0002` |
| 5b | `messages` + `conversations` | 1 + 1 | message id 8 and `911bf3e8…` (required by `ck_conversations_target`) |
| 6 | `listings` | 11 | 10 seed + `86b37ad4…` |
| 7 | `refresh_tokens` | 15 | the 7 test accounts |
| 8 | `users` | 7 | 7 confirmed test accounts |
| 9 | `books` | 1 | `95e946a4…` "E2E Physics Test" |
| 10 | `conversation_participants` (cascade) | 1 | participant row of the removed mixed conversation |
| | **total** | **60** | |

One transaction, committed only after the runner recomputed per-table deltas inside the transaction
and found every one exactly as approved. No statement outside the file ran.

### Correction to the earlier figures

The audit and plan documents said **56** rows. That was my arithmetic error — the original list sums
to **57**, and the executed variant A is **60** (57 + message 8 + the mixed conversation + its one
cascaded participant row). The approved *records* never changed; only my running total was wrong.
The documents have been corrected.

## 2. Before / after

| Table | Before | After | Δ |
| --- | ---: | ---: | ---: |
| users | 10 | **3** | −7 |
| refresh_tokens | 42 | **27** | −15 |
| listings | 11 | **0** | −11 |
| listing_images | 0 | 0 | 0 |
| listing_status_history | 0 | 0 | 0 |
| collections | 2 | **0** | −2 |
| collection_items | 4 | **0** | −4 |
| conversations | 4 | **0** | −4 |
| conversation_participants | 8 | **0** | −8 |
| messages | 8 | **0** | −8 |
| books | 13 | **12** | −1 |
| media_assets / saved_* / alerts / notifications / requests / exchanges / reports / moderation | 0 | 0 | 0 |
| institutions / aliases / categories / countries / states / districts / cities / location aliases / topics / topic aliases | 24 / 46 / 11 / 2 / 36 / 791 / 634 / 104 / 48 / 8 | **unchanged** | 0 |
| flyway_schema_history | 11 | **11** | 0 |
| **total rows** | 1 817 | **1 757** | **−60** |

`refresh_tokens` was 42 (not 41) at execution time: a login on the preserved `…1@gmail.com` account
landed between approval and execution. The 7 test accounts still held exactly 15 tokens, so the
approved deletion set was unaffected; the final count is 27 rather than the predicted 26.

## 3. Deleted records — exact IDs

**Users (7):** `a0000000-…-000000000001`, `a0000000-…-000000000002`, `a0000000-…-000000000003`,
`1c69f63f-378e-4c2c-8e4f-8905c621f945`, `ae4bc4f3-52f6-480b-be7e-1c880387b26a`,
`c56e4946-bd13-406b-a4f7-fd53d5b6307b`, `18c07ee3-63ac-4118-912c-d27d1fd7a909`

**Listings (11):** `d0000000-0000-4000-8000-000000000001` … `…000010`, `86b37ad4-983f-43a5-bda2-640b7620b1e2`

**Collections (2):** `c0000000-0000-4000-8000-000000000001`, `c0000000-0000-4000-8000-000000000002`

**Conversations (4):** `e0000000-0000-4000-8000-000000000001`, `e0000000-0000-4000-8000-000000000002`,
`425c738f-9afe-4e79-9f64-de4dfd89ce0c`, `911bf3e8-a665-4dac-bccf-37587d220c4d`

**Messages (8):** ids 1–8. **Refresh tokens (15):** all tokens of the 7 accounts.
**Books (1):** `95e946a4-5cec-40ca-98aa-f5760641f0d5`.

## 4. Preserved records

| Preserved | Evidence |
| --- | --- |
| `d400f3e2…` pahadiaarun@gmail.com | Google-authenticated founder account, 12 tokens |
| `0638b6b2…` pahadiaarun1@gmail.com | Active login today (last token 21:32 IST), 13→14 tokens |
| `346f5cfb…` devender.pahadia@gmail.com | Real-looking sign-up from 2026-11-06 07:56 |
| 12 `b0000000…` catalogue books | Neutral reference data |
| All geographic, institution, category and topic master data | Untouched |
| Flyway history | `{11}`, 11 successes |

Nothing named in the plan's "records that must be preserved" section was removed.

## 5. What went wrong first, and how it was handled

The original 56/57-row script was executed and **aborted by the database**:

```
ERROR: new row for relation "conversations" violates check constraint "ck_conversations_target"
```

`conversations` enforces `CHECK (listing_id IS NOT NULL OR request_id IS NOT NULL)`. The preserved
mixed conversation was anchored only by its listing, so detaching the test listing was illegal. The
transaction rolled back; production was byte-identical to the pre-execution snapshot; nothing was
retried automatically. After the blocker was explained and option A was approved, the script gained
STEP 5b and ran successfully.

**Root cause of the missed check:** the local dry-run schema was rebuilt from `information_schema`
with primary keys, unique constraints and foreign keys — but the generator omitted CHECK
constraints, so the copy accepted a state production rejects. The generator now emits CHECK
constraints, and the dry run **reproduces the exact production error**, then passes for variant A.
That gap is the main methodological lesson of this task.

## 6. Backup and recovery

| Item | Status |
| --- | --- |
| Aiven backup / PITR | Confirmed by the owner before execution |
| Pre-execution snapshot | `.prod-snapshot-preexec/` — 2026-11-06T16:21Z, 1 817 rows, restore-verified 42/42 |
| Post-cleanup snapshot | `.prod-snapshot/` — 1 757 rows, restore-verified 42/42 |
| Earlier snapshots | `.prod-snapshot-1508/`, `.prod-snapshot-1557/`, `.prod-snapshot-1604/` |
| Access | `chmod 700`, git-ignored — contain personal data and token hashes |

Restore to the pre-cleanup state: load `.prod-snapshot-preexec/` per
`PRODUCTION_CLEANUP_BACKUP_AND_RECOVERY.md` §3, or restore the Aiven backup/PITR point.

## 7. Post-cleanup verification

### Database integrity (production, read-only)

| Check | Result |
| --- | --- |
| Users remaining | 3 — exactly the three preserved accounts |
| Orphaned listings / collections / messages / participants / items / tokens | **0 / 0 / 0 / 0 / 0 / 0** |
| Foreign keys | all satisfied, no violations |
| Flyway | `{11}`, 11 successes, checksums untouched |
| Master data | institutions 24, districts 791, cities 634, categories 11 — unchanged |
| Books | 12 reference rows |

### Application verification

The public site (`resourceswaphub.work.gd`) resolves to an ISP block page
(`restricted.rpz.airtelspam.com`) from this network, so the deployed app could not be exercised
directly. Instead the **real backend was started against a local restore of the post-cleanup data**
(schema recreated with PKs, uniques, FKs and CHECKs; Flyway disabled so nothing could migrate):

| Check | Result |
| --- | --- |
| Spring Boot startup | `Started BookBridgeApplication in 3.003 seconds` |
| Hibernate `ddl-auto: validate` | **passed** — entities match the V11 schema |
| `/actuator/health` | `{"status":"UP"}` |
| `GET /api/v1/categories` | 200 with real category data |
| `GET /api/v1/discovery/listings?page=0&size=5` | 200, `totalElements: 0` — empty marketplace renders correctly |
| `GET /api/v1/discovery/filters` | 200 |
| `GET /api/v1/auth/providers` | 200 |
| `GET /api/v1/listings/<deleted id>` | 404 as expected |
| Connection target | verified via `pg_stat_activity` — 3 connections to the local copy, none to production |
| Cleanup | app stopped, local copy and temp storage removed |

### Not verified (and why)

* **Google Sign-In, profile, chat send, listing creation, requests, collections, saves, uploads,
  exchanges, moderation** — all need either real credentials or new records. Creating records in
  production was explicitly out of scope, and the deployed site is unreachable from this network.
  These remain unverified; the schema/entity validation above is the strongest check possible
  without touching production or a deployed environment.
* **Uploaded files** — there are no `media_assets` or `listing_images` rows and no object storage, so
  nothing existed to test or delete.

## 8. Storage objects removed

**None.** No media rows existed before or after; no bucket, no `./storage` artefacts referenced from
the database. Phase 7 had an empty work list and no file operation was performed.

## 9. Remaining issues

1. **The public domain appears blocked or lapsed** — `resourceswaphub.work.gd` resolves to an ISP
   spam-block page from this network. Worth confirming DNS from another resolver; unrelated to this
   cleanup but it blocks any end-to-end verification of production.
2. **No pending registrations.** Production now has 3 genuine accounts and zero listings, so the
   marketplace renders an empty state to real visitors until someone lists something.
3. **The `demo.sql` seed can be re-applied to production by mistake again** — its own header says
   "Never apply to production", yet its rows were there. Consider a guard (skip when
   `SPRING_PROFILES_ACTIVE=prod`, or a `WHERE current_database() <> 'bookbridge'` assertion).
4. **The dry-run schema generator needed CHECK constraints** — fixed in
   `resourceswaphub-database/scripts/tools/ProdSnapshot.java`; any future cleanup must regenerate
   `ddl.sql` to include them.
5. **Local copies were removed**, but `.prod-snapshot*` directories still hold production data.
   Delete them once you no longer need the rollback point.

## 10. Execution log

Full per-table before/after table with verdicts: `docs/production_cleanup_execution_log.md`.

```
books                            13 ->     12  delta -1  OK
collection_items                  4 ->      0  delta -4  OK
collections                       2 ->      0  delta -2  OK
conversation_participants         8 ->      0  delta -8  OK
conversations                     4 ->      0  delta -4  OK
listings                         11 ->      0  delta -11 OK
messages                          8 ->      0  delta -8  OK
refresh_tokens                   42 ->     27  delta -15 OK
users                            10 ->      3  delta -7  OK
total rows deleted: 60 (expected 60)
COMMITTED
```
