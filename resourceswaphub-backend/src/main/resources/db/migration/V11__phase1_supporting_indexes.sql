-- V11: Phase 1 supporting indexes.
--
-- Purely additive: no table, column, constraint or row is touched, and every
-- statement is idempotent so a partially applied run can be replayed safely.
-- Migrations V1..V10 are untouched.
--
-- These are the access paths the Phase 1 services need that V10 did not create.
-- Each one is referenced by the code comment that depends on it, so removing one
-- is a visible decision rather than a silent slowdown.

-- ---------------------------------------------------------------------------
-- 1. notification dedupe lookups
-- ---------------------------------------------------------------------------
-- Notification fan-out asks "has this user already been told about this entity
-- of this type, and have they read it" once per candidate match. Without this,
-- every published listing does a per-user scan of that user's notifications.
CREATE INDEX IF NOT EXISTS idx_notifications_user_type_entity
    ON notifications (user_id, type, entity_id);

-- ---------------------------------------------------------------------------
-- 2. saved lists ordering
-- ---------------------------------------------------------------------------
-- Both saved lists are read in save-date order. The composite primary key gives
-- the user prefix but orders by the resource id, so the sort was unindexed.
CREATE INDEX IF NOT EXISTS idx_saved_resources_user_created
    ON saved_resources (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_saved_collections_user_created
    ON saved_collections (user_id, created_at DESC);
