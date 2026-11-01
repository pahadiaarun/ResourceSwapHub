-- V9: Indexes for discovery filtered by standardized location.
--
-- The location foreign keys land in V8; the browse path always narrows to AVAILABLE listings, so
-- these partial indexes cover the shapes the discovery query actually asks for (state, then city
-- inside a state) without indexing rows nobody browses. See docs/INDEXING_STRATEGY.md.

CREATE INDEX idx_listings_state_discover
    ON listings (state_id, city_id)
    WHERE status = 'AVAILABLE' AND state_id IS NOT NULL;

CREATE INDEX idx_listings_city_discover
    ON listings (city_id)
    WHERE status = 'AVAILABLE' AND city_id IS NOT NULL;

CREATE INDEX idx_collections_state ON collections (state_id) WHERE state_id IS NOT NULL;
CREATE INDEX idx_collections_city ON collections (city_id) WHERE city_id IS NOT NULL;

-- Backfill asks "is this city name used by exactly one row in the whole directory?" for records
-- that never stored a state; the scoped unique key in V8 is (state_id, normalized_name), so the
-- name alone needs its own index.
CREATE INDEX idx_cities_normalized ON cities (normalized_name);
