# Production Test Data Cleanup Plan

**Target:** Aiven PostgreSQL `bookbridge` @ `resourceswaphub-pahadiaarun-a976.a.aivencloud.com:20013`
**Prepared:** 2026-11-06
**Script:** `resourceswaphub-database/scripts/production_test_data_cleanup.sql` — **drafted, unexecuted. On 2026-11-06 the owner chose "audit only, no cleanup", so it stays unexecuted and needs a fresh explicit go before any DELETE runs.**
**Audit:** `docs/PRODUCTION_TEST_DATA_AUDIT.md` · **Backup:** `docs/PRODUCTION_CLEANUP_BACKUP_AND_RECOVERY.md`

> This plan removes **56 rows** and nothing else. It deletes 7 accounts that were created by the
> development seed file or by automated test harnesses, together with the listings, collections,
> conversations, messages and tokens that belong to them. Every row is named by explicit UUID.
> Three real-person accounts, all master data, the Flyway history and the one mixed conversation are
> preserved.

---

## 1. Exact records proposed for deletion

### 1.1 Users — 7

| id | E-mail | Why |
| --- | --- | --- |
| `a0000000-0000-4000-8000-000000000001` | aisha@bookbridge.demo | Seed UUID in `demo.sql`; `.demo` domain |
| `a0000000-0000-4000-8000-000000000002` | rohan@bookbridge.demo | Seed UUID in `demo.sql` |
| `a0000000-0000-4000-8000-000000000003` | meera@bookbridge.demo | Seed UUID in `demo.sql` |
| `1c69f63f-378e-4c2c-8e4f-8905c621f945` | e2e-1793524472@bookbridge.dev | Automated e2e account (epoch suffix) |
| `ae4bc4f3-52f6-480b-be7e-1c880387b26a` | e2e-1793524508@bookbridge.dev | Automated e2e account, synthetic listing |
| `c56e4946-bd13-406b-a4f7-fd53d5b6307b` | probe1793539481@example.com | Scripted probe account |
| `18c07ee3-63ac-4118-912c-d27d1fd7a909` | uitest.pass+nov2@resourceswaphub.test | Reserved `.test` TLD, "UI Test Pass" |

### 1.2 Listings — 11

`d0000000-0000-4000-8000-000000000001` … `…000010` (10 seed listings, owners U1–U3) and
`86b37ad4-983f-43a5-bda2-640b7620b1e2` ("E2E Physics Test").

### 1.3 Collections — 2 · Collection items — 4

`c0000000-0000-4000-8000-000000000001`, `c0000000-0000-4000-8000-000000000002`;
items: `c…0001→d…0001`, `c…0001→d…0002`, `c…0002→d…0006`, `c…0002→d…0007`.

### 1.4 Conversations — 3 · Participants — 7 · Messages — 7

Conversations `e0000000-0000-4000-8000-000000000001`, `e0000000-0000-4000-8000-000000000002`,
`425c738f-9afe-4e79-9f64-de4dfd89ce0c` (both participants are test accounts).
Messages `id IN (1,2,3,4,5,6,7)`. Participant rows are selected by the 7 user IDs above.

### 1.5 Refresh tokens — 15 · Catalogue — 1

All `refresh_tokens` of the 7 accounts (15 rows); `books` row `95e946a4-5cec-40ca-98aa-f5760641f0d5`
("E2E Physics Test" by "E2E Author").

---

## 2. Table-wise deletion counts

| # | Table | Rows deleted | Remaining | Selection |
| ---: | --- | ---: | ---: | --- |
| 1 | `messages` | 7 | 1 | `id IN (1..7)` |
| 2 | `conversation_participants` | 7 | 1 | `user_id IN (7 test ids)` |
| 3 | `conversations` | 3 | 1 | 3 explicit conversation ids |
| 4 | `collection_items` | 4 | 0 | `collection_id IN (2 ids)` |
| 5 | `collections` | 2 | 0 | 2 explicit ids |
| 6 | `listings` | 11 | 0 | 11 explicit ids |
| 7 | `refresh_tokens` | 15 | 26 | `user_id IN (7 test ids)` |
| 8 | `users` | 7 | 3 | 7 explicit ids |
| 9 | `books` | 1 | 12 | 1 explicit id |
| | **total** | **56** | | out of 1 816 rows |

Every step is a `DELETE ... WHERE <explicit ids>` inside a `DO` block that asserts
`GET DIAGNOSTICS row_count`. A count other than the expected one (or 0 on a re-run) raises an
exception and aborts the whole transaction.

---

## 3. Records that must be preserved

| Preserved | Count | Reason |
| --- | ---: | --- |
| `pahadiaarun@gmail.com` (`d400f3e2…`) | 1 user + 12 tokens | Real Google identity, founder |
| `pahadiaarun1@gmail.com` (`0638b6b2…`) | 1 user + 13 tokens + 1 message | POTENTIAL_TEST, but authenticated 2026-11-06 20:15 IST — a live login |
| `devender.pahadia@gmail.com` (`346f5cfb…`) | 1 user + 1 token | POTENTIAL_TEST; plausible real sign-up |
| Conversation `911bf3e8…` | 1 conversation | Mixed: test user + preserved account |
| Message `id = 8` | 1 message | Authored by the preserved account |
| `b0000000…` catalogue books | 12 | Neutral reference data |
| institutions / aliases / categories / countries / states / districts / cities / location aliases / topics / topic aliases | 24 / 46 / 11 / 2 / 36 / 791 / 634 / 104 / 48 / 8 | Shared master data |
| `flyway_schema_history` | 11 | Never modified |
| Users U8, U9, U10 with no listings | — | "no listings" is explicitly not evidence of test data |

---

## 4. Foreign-key and cascade impact

Deletes run **leaf-first**, so no approved record is reached by a cascade the plan did not name:

```
messages (explicit ids)
  → conversation_participants (by user) → conversations (explicit ids)
  → collection_items (by collection) → collections (explicit ids)
  → listings (explicit ids)
  → refresh_tokens (by user)
  → users (explicit ids)
  → books (explicit id, after RESTRICT parent is gone)
```

Consequences to note:

* `conversations.listing_id` is `ON DELETE SET NULL`, so deleting listing `86b37ad4…` will set
  `listing_id = NULL` on the preserved mixed conversation. **This is expected and verified in the
  dry run.**
* `messages.sender_id` and `conversation_participants.user_id` are `ON DELETE CASCADE`; because every
  dependent row is deleted explicitly first, the user delete triggers no hidden cascade. The one
  unavoidable exception: the test participant's own row inside the mixed conversation necessarily
  disappears with the test account.
* `listings.book_id` is `ON DELETE RESTRICT`; the synthetic book is therefore deleted *after* its
  listing.
* `institution_suggestions.suggested_by` is `NO ACTION` (would block a user delete) — the table is
  empty, so it cannot block.
* No constraint is disabled, deferred or dropped. No `TRUNCATE`, no `DROP TABLE`, no DDL.

---

## 5. Media / file cleanup impact

**None.** `media_assets` = 0, `listing_images` = 0, no `digital_media_id`, no `external_url`, no
`cover_media_id`. There is no S3/R2 bucket; uploads would live under `./storage` on the API host and
no database row points there. Phase 7 has an empty work list — no file will be deleted.

---

## 6. Expected database state after cleanup

| Table | Before | After |
| --- | ---: | ---: |
| users | 10 | **3** |
| listings | 11 | **0** |
| collections | 2 | **0** |
| collection_items | 4 | **0** |
| conversations | 4 | **1** |
| conversation_participants | 8 | **1** |
| messages | 8 | **1** |
| refresh_tokens | 41 | **26** |
| books | 13 | **12** |
| all other tables | unchanged | unchanged |
| total rows | 1 816 | **1 760** |

The application keeps working: three accounts remain, the catalogue remains, master data remains,
and `ddl-auto: validate` still matches the untouched Flyway schema.

---

## 7. Proposed SQL

Full script: **`resourceswaphub-database/scripts/production_test_data_cleanup.sql`** (344 lines, `BEGIN … COMMIT`,
`COMMIT` deliberately commented out).

Shape of every step:

```sql
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM messages WHERE id IN (1, 2, 3, 4, 5, 6, 7);
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 7) THEN
        RAISE EXCEPTION 'messages: deleted % rows, expected 7 (0 on a re-run)', n;
    END IF;
END
$step$;
```

Guards before any delete:

* `current_database() <> 'bookbridge'` → abort (the only bypass is a `bookbridge.cleanup_dryrun`
  GUC used solely by the local dry-run harness; production never sets it).
* `users` count must be 10 (before) or 3 (after) → abort otherwise.
* The founder's anchor account `d400f3e2…` must exist → abort otherwise (wrong-database tripwire).
* The preserved mixed conversation `911bf3e8…` must exist → abort otherwise.

---

## 8. Dry-run evidence (local, never production)

The script was executed end-to-end against a **throwaway local database** rebuilt from the verified
snapshot, with the real foreign keys, primary keys and unique constraints recreated:

```
resourceswaphub-database/scripts/tools/LocalDryRun.java load .prod-snapshot prod_cleanup_dryrun
resourceswaphub-database/scripts/tools/LocalDryRun.java run  prod_cleanup_dryrun <script with COMMIT enabled>
```

| Check | Result |
| --- | --- |
| Guards | fired correctly when the DB name differed (`Refusing to run: current_database() is …`) |
| All 9 delete steps | row counts matched exactly (7, 7, 3, 4, 2, 11, 15, 7, 1) |
| Final state | users 3 · listings 0 · collections 0 · items 0 · conversations 1 · participants 1 · messages 1 · tokens 26 · books 12 |
| Mixed conversation | survived with `listing_id = NULL` and 1 participant — as predicted |
| Orphan check | 0 orphaned listings, collections, messages, participants, collection items |
| Flyway | still `{11}`, 11 successful migrations |
| Idempotency | second run produced identical state, no exception |
| Cleanup | throwaway database dropped afterwards |

This is the strongest evidence available without touching production: the SQL, its guards, its row
counts, its constraint behaviour and its final state are all verified.

---

## 9. Rollback and recovery

1. The entire script is one transaction; before `COMMIT` a rollback restores the prior state exactly.
2. After `COMMIT`, recovery requires a restore: take a **fresh** logical snapshot immediately before
   running (`.prod-snapshot/`, procedure in `PRODUCTION_CLEANUP_BACKUP_AND_RECOVERY.md`) and confirm
   the Aiven backup/PITR point in the console.
3. There is **no soft delete** in this schema, so no application-level undo exists.

---

## 10. Post-cleanup verification queries

Already embedded at the end of the script (STEP 10) and repeatable afterwards:

```sql
-- counts must match §6
SELECT (SELECT count(*) FROM users) users,
       (SELECT count(*) FROM listings) listings,
       (SELECT count(*) FROM collections) collections,
       (SELECT count(*) FROM collection_items) items,
       (SELECT count(*) FROM conversations) conversations,
       (SELECT count(*) FROM conversation_participants) participants,
       (SELECT count(*) FROM messages) messages,
       (SELECT count(*) FROM refresh_tokens) tokens,
       (SELECT count(*) FROM books) books;

-- no dangling references
SELECT count(*) FILTER (WHERE u.id IS NULL) FROM listings l LEFT JOIN users u ON u.id = l.owner_id;
SELECT count(*) FILTER (WHERE c.id IS NULL) FROM messages m LEFT JOIN conversations c ON c.id = m.conversation_id;

-- schema untouched
SELECT max(string_to_array(version,'.')::int[]) FROM flyway_schema_history WHERE success;  -- {11}
```

Application smoke tests (read-only, no test records created in production) should confirm: Google
sign-in for the three preserved accounts, profile load, discovery browse (empty state), collections
(empty state), inbox (one thread), notifications (empty), and 404 handling for a deleted listing id.

---

## 11. Risks

| Risk | Severity | Mitigation |
| --- | --- | --- |
| A preserved user is actually a test account (U9/U10) | low | Deliberately kept; separate decision |
| New test rows appear between snapshot and execution | low | Re-run identity guards + fresh snapshot immediately before |
| Production receives a deploy mid-cleanup | low | Run in a quiet window; guards re-check `users` count |
| Mixed conversation left as a 1-participant NULL-listing thread | cosmetic | Optional block in the script (needs separate approval) |
| Aiven backup state unknown | medium | Owner confirms in console; verified logical snapshot exists |
| Row counts drift because someone signs up during execution | low | Script aborts on any unexpected count rather than guessing |

---

## 12. Approval required

Approval must name this plan: **56 rows, 7 users, 11 listings, 2 collections, 3 conversations,
7 messages, 15 refresh tokens, 1 catalogue row**, with `pahadiaarun@gmail.com`,
`pahadiaarun1@gmail.com`, `devender.pahadia@gmail.com`, conversation `911bf3e8…`, message `id = 8`
and all master data preserved.

Approval of the audit, or of this document's existence, is **not** approval to delete. Until you
explicitly approve these records, production stays exactly as it is.
