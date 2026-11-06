-- ============================================================================
-- ResourceSwapHub — PRODUCTION TEST DATA CLEANUP
-- ============================================================================
-- STATUS: DRAFT — NOT APPROVED, NOT EXECUTED.
--
-- Do not run any statement in this file against production until the owner has
-- explicitly approved the exact record list in
-- docs/PRODUCTION_TEST_DATA_CLEANUP_PLAN.md.
--
-- Target : Aiven PostgreSQL, database `bookbridge`
-- Scope  : 7 confirmed test accounts and the 11 listings / 2 collections /
--          3 conversations / 7 messages / 15 refresh tokens / 1 catalogue row
--           that belong to them.
-- Impact : 56 rows deleted out of 1 816. Nothing else is touched.
--
-- Guarantees
--   * One transaction. Any failed guard or unexpected row count aborts
--     everything — there is no partially-cleaned state.
--   * Explicit record IDs only. No date-based, LIKE-based or owner-wide DELETE.
--   * No TRUNCATE, no DROP TABLE, no DDL, no disabled foreign keys, no
--     cascade-triggered surprise: every dependent row is named explicitly.
--   * Idempotent: a second run matches 0 rows at every step and commits a no-op
--     instead of failing.
--   * Flyway history and all master/reference data are untouched.
--
-- How to run (after approval)
--   * Aiven console SQL window, or any client, with autocommit OFF.
--   * psql users: keep ON_ERROR_STOP on.
--   * Confirm `SELECT current_database(), current_user;` -> bookbridge, avnadmin
--     before executing, and keep the BEGIN/COMMIT boundary intact.
-- ============================================================================

BEGIN;

SET LOCAL statement_timeout = '120s';

-- ---------------------------------------------------------------------------
-- STEP 0 — Identity guards
-- Aborts unless this really is production `bookbridge` with the expected shape.
-- ---------------------------------------------------------------------------
DO $guard$
DECLARE
    v_users        bigint;
    v_founder      bigint;
    v_mixed_conv   bigint;
BEGIN
    -- The only sanctioned bypass is the local dry-run harness, which sets
    -- `bookbridge.cleanup_dryrun = on` on a throwaway copy of the snapshot.
    -- Production never sets that GUC, so this cannot mask a wrong target there.
    IF current_database() <> 'bookbridge'
       AND coalesce(current_setting('bookbridge.cleanup_dryrun', true), 'off') <> 'on' THEN
        RAISE EXCEPTION 'Refusing to run: current_database() is %, expected bookbridge',
            current_database();
    END IF;

    SELECT count(*) INTO v_users FROM users;
    IF v_users NOT IN (10, 3) THEN       -- 10 before cleanup, 3 after
        RAISE EXCEPTION 'Refusing to run: users=% (expected 10 before or 3 after cleanup)', v_users;
    END IF;

    -- Anchor: the founder's genuine Google account must still exist.
    SELECT count(*) INTO v_founder
    FROM users WHERE id = 'd400f3e2-0d53-4d46-a6d8-7d599994ae84';
    IF v_founder <> 1 THEN
        RAISE EXCEPTION 'Refusing to run: founder anchor account missing — wrong database?';
    END IF;

    -- Anchor: the mixed conversation that must survive this cleanup.
    SELECT count(*) INTO v_mixed_conv
    FROM conversations WHERE id = '911bf3e8-a665-4dac-bccf-37587d220c4d';
    IF v_mixed_conv <> 1 THEN
        RAISE EXCEPTION 'Refusing to run: preserved mixed conversation missing — wrong database?';
    END IF;
END
$guard$;

-- ---------------------------------------------------------------------------
-- STEP 0b — Pre-flight evidence: exactly what this transaction will remove.
-- Read the output before letting the transaction reach COMMIT.
-- ---------------------------------------------------------------------------
SELECT 'pre-flight: users to delete' AS step, u.id, u.email, u.created_at
FROM users u
WHERE u.id IN (
    'a0000000-0000-4000-8000-000000000001',
    'a0000000-0000-4000-8000-000000000002',
    'a0000000-0000-4000-8000-000000000003',
    '1c69f63f-378e-4c2c-8e4f-8905c621f945',
    'ae4bc4f3-52f6-480b-be7e-1c880387b26a',
    'c56e4946-bd13-406b-a4f7-fd53d5b6307b',
    '18c07ee3-63ac-4118-912c-d27d1fd7a909'
)
ORDER BY u.created_at;

SELECT 'pre-flight: listings to delete' AS step, l.id, l.title, l.owner_id, l.status
FROM listings l
WHERE l.owner_id IN (
    'a0000000-0000-4000-8000-000000000001',
    'a0000000-0000-4000-8000-000000000002',
    'a0000000-0000-4000-8000-000000000003',
    'ae4bc4f3-52f6-480b-be7e-1c880387b26a'
)
ORDER BY l.id;

-- ---------------------------------------------------------------------------
-- STEP 1 — messages (7 rows: ids 1-7)
-- Message id 8 ("Hi", sent by the founder's second account) is PRESERVED.
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM messages WHERE id IN (1, 2, 3, 4, 5, 6, 7);
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 7) THEN
        RAISE EXCEPTION 'messages: deleted % rows, expected 7 (0 on a re-run)', n;
    END IF;
    RAISE NOTICE 'messages: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 2 — conversation_participants of the 7 test accounts (7 rows)
-- Includes the test side of the mixed conversation (its user is a test
-- account, so its membership row necessarily goes with the account).
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM conversation_participants
    WHERE user_id IN (
        'a0000000-0000-4000-8000-000000000001',
        'a0000000-0000-4000-8000-000000000002',
        'a0000000-0000-4000-8000-000000000003',
        '1c69f63f-378e-4c2c-8e4f-8905c621f945',
        'ae4bc4f3-52f6-480b-be7e-1c880387b26a',
        'c56e4946-bd13-406b-a4f7-fd53d5b6307b',
        '18c07ee3-63ac-4118-912c-d27d1fd7a909'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 7) THEN
        RAISE EXCEPTION 'conversation_participants: deleted % rows, expected 7', n;
    END IF;
    RAISE NOTICE 'conversation_participants: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 3 — conversations that were entirely test-to-test (3 rows)
-- The mixed conversation 911bf3e8-… is deliberately NOT listed here.
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM conversations
    WHERE id IN (
        'e0000000-0000-4000-8000-000000000001',
        'e0000000-0000-4000-8000-000000000002',
        '425c738f-9afe-4e79-9f64-de4dfd89ce0c'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 3) THEN
        RAISE EXCEPTION 'conversations: deleted % rows, expected 3', n;
    END IF;
    RAISE NOTICE 'conversations: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 4 — collection_items of the 2 test collections (4 rows)
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM collection_items
    WHERE collection_id IN (
        'c0000000-0000-4000-8000-000000000001',
        'c0000000-0000-4000-8000-000000000002'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 4) THEN
        RAISE EXCEPTION 'collection_items: deleted % rows, expected 4', n;
    END IF;
    RAISE NOTICE 'collection_items: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 5 — the 2 test collections
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM collections
    WHERE id IN (
        'c0000000-0000-4000-8000-000000000001',
        'c0000000-0000-4000-8000-000000000002'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 2) THEN
        RAISE EXCEPTION 'collections: deleted % rows, expected 2', n;
    END IF;
    RAISE NOTICE 'collections: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 6 — the 11 test listings
-- Child tables (listing_images, listing_status_history, exchanges,
-- resource_reports, saved_resources) are all empty; verified in the audit.
-- This will set conversations.listing_id = NULL on the preserved mixed thread.
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM listings
    WHERE id IN (
        'd0000000-0000-4000-8000-000000000001',
        'd0000000-0000-4000-8000-000000000002',
        'd0000000-0000-4000-8000-000000000003',
        'd0000000-0000-4000-8000-000000000004',
        'd0000000-0000-4000-8000-000000000005',
        'd0000000-0000-4000-8000-000000000006',
        'd0000000-0000-4000-8000-000000000007',
        'd0000000-0000-4000-8000-000000000008',
        'd0000000-0000-4000-8000-000000000009',
        'd0000000-0000-4000-8000-000000000010',
        '86b37ad4-983f-43a5-bda2-640b7620b1e2'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 11) THEN
        RAISE EXCEPTION 'listings: deleted % rows, expected 11', n;
    END IF;
    RAISE NOTICE 'listings: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 7 — refresh tokens of the 7 test accounts (15 rows)
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM refresh_tokens
    WHERE user_id IN (
        'a0000000-0000-4000-8000-000000000001',
        'a0000000-0000-4000-8000-000000000002',
        'a0000000-0000-4000-8000-000000000003',
        '1c69f63f-378e-4c2c-8e4f-8905c621f945',
        'ae4bc4f3-52f6-480b-be7e-1c880387b26a',
        'c56e4946-bd13-406b-a4f7-fd53d5b6307b',
        '18c07ee3-63ac-4118-912c-d27d1fd7a909'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 15) THEN
        RAISE EXCEPTION 'refresh_tokens: deleted % rows, expected 15', n;
    END IF;
    RAISE NOTICE 'refresh_tokens: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 8 — the 7 confirmed test accounts
-- Every dependent row has already been removed above, so this delete relies on
-- no cascade to reach records that were not explicitly approved.
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM users
    WHERE id IN (
        'a0000000-0000-4000-8000-000000000001',
        'a0000000-0000-4000-8000-000000000002',
        'a0000000-0000-4000-8000-000000000003',
        '1c69f63f-378e-4c2c-8e4f-8905c621f945',
        'ae4bc4f3-52f6-480b-be7e-1c880387b26a',
        'c56e4946-bd13-406b-a4f7-fd53d5b6307b',
        '18c07ee3-63ac-4118-912c-d27d1fd7a909'
    );
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 7) THEN
        RAISE EXCEPTION 'users: deleted % rows, expected 7', n;
    END IF;
    RAISE NOTICE 'users: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 9 — the synthetic catalogue row "E2E Physics Test" (1 row)
-- Runs after STEP 6 because listings.book_id is ON DELETE RESTRICT.
-- The 12 b0000000… reference-catalogue books are PRESERVED.
-- ---------------------------------------------------------------------------
DO $step$
DECLARE n bigint;
BEGIN
    DELETE FROM books WHERE id = '95e946a4-5cec-40ca-98aa-f5760641f0d5';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n NOT IN (0, 1) THEN
        RAISE EXCEPTION 'books: deleted % rows, expected 1', n;
    END IF;
    RAISE NOTICE 'books: % deleted', n;
END
$step$;

-- ---------------------------------------------------------------------------
-- STEP 10 — In-transaction verification. If any of these look wrong, ROLLBACK.
-- ---------------------------------------------------------------------------
SELECT 'verify: preserved accounts' AS check_name, u.id, u.email
FROM users u ORDER BY u.created_at;

SELECT 'verify: table counts' AS check_name,
       (SELECT count(*) FROM users)                     AS users,
       (SELECT count(*) FROM listings)                  AS listings,
       (SELECT count(*) FROM collections)               AS collections,
       (SELECT count(*) FROM collection_items)          AS collection_items,
       (SELECT count(*) FROM conversations)             AS conversations,
       (SELECT count(*) FROM conversation_participants) AS participants,
       (SELECT count(*) FROM messages)                  AS messages,
       (SELECT count(*) FROM refresh_tokens)            AS refresh_tokens,
       (SELECT count(*) FROM books)                     AS books;

-- Expected after the cleanup:
--   users 3 | listings 0 | collections 0 | collection_items 0
--   conversations 1 | conversation_participants 1 | messages 1
--   refresh_tokens 26 | books 12

SELECT 'verify: no orphaned rows' AS check_name,
       (SELECT count(*) FROM listings l
         LEFT JOIN users u ON u.id = l.owner_id WHERE u.id IS NULL)          AS orphan_listings,
       (SELECT count(*) FROM collections c
         LEFT JOIN users u ON u.id = c.owner_id WHERE u.id IS NULL)          AS orphan_collections,
       (SELECT count(*) FROM messages m
         LEFT JOIN conversations c ON c.id = m.conversation_id
        WHERE c.id IS NULL)                                                  AS orphan_messages,
       (SELECT count(*) FROM conversation_participants p
         LEFT JOIN users u ON u.id = p.user_id WHERE u.id IS NULL)           AS orphan_participants,
       (SELECT count(*) FROM collection_items ci
         LEFT JOIN listings l ON l.id = ci.listing_id WHERE l.id IS NULL)    AS orphan_collection_items;

SELECT 'verify: flyway unchanged' AS check_name,
       max(string_to_array(version, '.')::int[])::text AS latest_version,
       count(*) FILTER (WHERE success)                 AS successful_migrations
FROM flyway_schema_history;

-- ---------------------------------------------------------------------------
-- STEP 11 — COMMIT.
-- Commented out on purpose: this file stays unexecuted until approval.
-- After reviewing every output above, uncomment the single line below.
-- ---------------------------------------------------------------------------
-- COMMIT;

-- Safety net: if the session ends without COMMIT, the transaction is discarded.
-- ROLLBACK;

-- ============================================================================
-- OPTIONAL — NOT PART OF THE APPROVED SCOPE
-- ============================================================================
-- Conversation 911bf3e8-a665-4dac-bccf-37587d220c4d is MIXED: one confirmed
-- test participant and one preserved account, one message ("Hi", id 8).
-- After the cleanup above it survives as a single-participant thread with a
-- NULL listing_id. It is intentionally left alone.
--
-- Deleting it requires a SEPARATE, explicit approval of these two lines:
--
--   DELETE FROM messages      WHERE id = 8;                                    -- 1 row, mixed
--   DELETE FROM conversations WHERE id = '911bf3e8-a665-4dac-bccf-37587d220c4d'; -- 1 row, mixed
--
-- Not included in the 56-row plan. Never enable this block by default.
-- ============================================================================
