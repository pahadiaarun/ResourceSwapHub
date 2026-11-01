-- V8: Normalized geographic locations (Country / State / District / City) with a shared
--     alias table, plus standardized foreign keys layered onto users, listings and collections.
--
-- Design notes:
--   * Additive only. The existing free-text `city`, `locality`, `state`, `country` columns
--     (V1/V2/V5) are preserved so the deployed frontend and `ddl-auto: validate` keep working.
--     The new *_id columns are nullable and backfilled by the LocationBackfillRunner; nothing
--     is dropped here. See docs/LOCATION_SCHEMA.md.
--   * A location is identified by its surrogate id + the parent scope, never by name alone:
--     the uniqueness constraints are (country, state name) and (state, city name) so two cities
--     with the same name in different states remain distinct rows.
--   * `official_code` holds publisher-provided identifiers (ISO 3166-2:IN, LGD / Census codes).
--     Curated seed rows leave it NULL where no official identifier is available rather than
--     fabricating one.
--   * `set_updated_at()` is defined in V1; `pg_trgm` is enabled in V5.

-- ---------------------------------------------------------------------------
-- 1) Reference hierarchy.
-- ---------------------------------------------------------------------------

CREATE TABLE countries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    iso_code VARCHAR(2) NOT NULL,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(100),
    source_url VARCHAR(500),
    dataset_version VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_countries_iso UNIQUE (iso_code),
    CONSTRAINT uq_countries_normalized UNIQUE (normalized_name)
);

CREATE INDEX idx_countries_normalized_trgm ON countries USING gin (normalized_name gin_trgm_ops);

CREATE TABLE states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id UUID NOT NULL REFERENCES countries(id) ON DELETE RESTRICT,
    official_code VARCHAR(10),
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    state_type VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(100),
    source_url VARCHAR(500),
    dataset_version VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A state name is unique within its country, so "Delhi" (India) and a hypothetical
    -- "Delhi" elsewhere never collide.
    CONSTRAINT uq_states_country_name UNIQUE (country_id, normalized_name),
    CONSTRAINT ck_states_official_code UNIQUE (official_code),
    CONSTRAINT ck_states_type CHECK (state_type IN ('STATE', 'UNION_TERRITORY'))
);

CREATE INDEX idx_states_country ON states (country_id);
CREATE INDEX idx_states_normalized_trgm ON states USING gin (normalized_name gin_trgm_ops);

CREATE TABLE districts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id UUID NOT NULL REFERENCES states(id) ON DELETE RESTRICT,
    official_code VARCHAR(20),
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(100),
    source_url VARCHAR(500),
    dataset_version VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_districts_state_name UNIQUE (state_id, normalized_name),
    CONSTRAINT ck_districts_official_code UNIQUE (official_code)
);

CREATE INDEX idx_districts_state ON districts (state_id);
CREATE INDEX idx_districts_normalized_trgm ON districts USING gin (normalized_name gin_trgm_ops);

CREATE TABLE cities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- District is optional because not every source maps a settlement to a district.
    district_id UUID REFERENCES districts(id) ON DELETE SET NULL,
    state_id UUID NOT NULL REFERENCES states(id) ON DELETE RESTRICT,
    official_code VARCHAR(20),
    name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    city_type VARCHAR(30),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(100),
    source_url VARCHAR(500),
    dataset_version VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Never key a city on its name alone: uniqueness is scoped to the parent state.
    CONSTRAINT uq_cities_state_name UNIQUE (state_id, normalized_name),
    CONSTRAINT ck_cities_official_code UNIQUE (official_code),
    CONSTRAINT ck_cities_type CHECK (city_type IS NULL OR city_type IN
        ('METROPOLITAN_CITY', 'CITY', 'TOWN', 'CENSUS_TOWN', 'VILLAGE', 'NOTIFICATION_AREA', 'OTHER'))
);

CREATE INDEX idx_cities_state ON cities (state_id);
CREATE INDEX idx_cities_district ON cities (district_id);
CREATE INDEX idx_cities_normalized_trgm ON cities USING gin (normalized_name gin_trgm_ops);

-- Polymorphic aliases so "Bengaluru" / "Bangalore", "Bombay" / "Mumbai", etc. resolve to one
-- canonical record. entity_id is intentionally not a FK (it points across four tables); the
-- import service is the only writer and always supplies a real id for the given entity_type.
CREATE TABLE location_aliases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(20) NOT NULL,
    entity_id UUID NOT NULL,
    alias VARCHAR(150) NOT NULL,
    normalized_alias VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_location_aliases_type CHECK (entity_type IN ('COUNTRY', 'STATE', 'DISTRICT', 'CITY')),
    CONSTRAINT uq_location_aliases_entity_alias UNIQUE (entity_type, entity_id, normalized_alias)
);

CREATE INDEX idx_location_aliases_normalized ON location_aliases USING gin (normalized_alias gin_trgm_ops);
CREATE INDEX idx_location_aliases_entity ON location_aliases (entity_type, entity_id);

CREATE TRIGGER trg_countries_updated_at BEFORE UPDATE ON countries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_states_updated_at BEFORE UPDATE ON states
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_districts_updated_at BEFORE UPDATE ON districts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_cities_updated_at BEFORE UPDATE ON cities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 2) Standardized foreign keys layered alongside the free-text columns.
--    Nullable: the backfill runner fills them where the existing text resolves to exactly
--    one canonical record, and leaves them NULL otherwise so no wrong city is ever assigned.
-- ---------------------------------------------------------------------------

ALTER TABLE users
    ADD COLUMN country_id UUID REFERENCES countries(id) ON DELETE SET NULL,
    ADD COLUMN state_id UUID REFERENCES states(id) ON DELETE SET NULL,
    ADD COLUMN city_id UUID REFERENCES cities(id) ON DELETE SET NULL;

CREATE INDEX idx_users_city ON users (city_id) WHERE city_id IS NOT NULL;
CREATE INDEX idx_users_state ON users (state_id) WHERE state_id IS NOT NULL;

ALTER TABLE listings
    ADD COLUMN country_id UUID REFERENCES countries(id) ON DELETE SET NULL,
    ADD COLUMN state_id UUID REFERENCES states(id) ON DELETE SET NULL,
    ADD COLUMN city_id UUID REFERENCES cities(id) ON DELETE SET NULL;

ALTER TABLE collections
    ADD COLUMN country_id UUID REFERENCES countries(id) ON DELETE SET NULL,
    ADD COLUMN state_id UUID REFERENCES states(id) ON DELETE SET NULL,
    ADD COLUMN city_id UUID REFERENCES cities(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 3) Starter reference data.
--    Countries: the two ISO 3166-1 codes this product needs at launch (IN + a neutral default).
--    Indian States/UTs and their ISO 3166-2:IN codes are the constitutional list from the brief
--    (Part 1 Step 2) — factual and verifiable, not scraped and not fabricated. Districts and
--    cities are NOT seeded here; they arrive only through the documented import pipeline
--    (LocationImportRunner) from an authorized dataset, because a reliable city-level source is
--    required to avoid inventing records. See docs/LOCATION_DATA_SOURCES.md.
-- ---------------------------------------------------------------------------

INSERT INTO countries (iso_code, name, normalized_name)
SELECT v.iso_code, v.name, btrim(lower(regexp_replace(v.name, '[^A-Za-z0-9 ]', '', 'g')))
FROM (VALUES
    ('IN', 'India'),
    ('US', 'United States')
) AS v(iso_code, name)
ON CONFLICT (iso_code) DO NOTHING;

INSERT INTO states (country_id, official_code, name, normalized_name, state_type)
SELECT
    (SELECT id FROM countries WHERE iso_code = 'IN'),
    v.code,
    v.name,
    btrim(lower(regexp_replace(v.name, '[^A-Za-z0-9 ]', '', 'g'))),
    v.state_type
FROM (VALUES
    ('IN-AP', 'Andhra Pradesh', 'STATE'),
    ('IN-AR', 'Arunachal Pradesh', 'STATE'),
    ('IN-AS', 'Assam', 'STATE'),
    ('IN-BR', 'Bihar', 'STATE'),
    ('IN-CT', 'Chhattisgarh', 'STATE'),
    ('IN-GA', 'Goa', 'STATE'),
    ('IN-GJ', 'Gujarat', 'STATE'),
    ('IN-HR', 'Haryana', 'STATE'),
    ('IN-HP', 'Himachal Pradesh', 'STATE'),
    ('IN-JH', 'Jharkhand', 'STATE'),
    ('IN-KA', 'Karnataka', 'STATE'),
    ('IN-KL', 'Kerala', 'STATE'),
    ('IN-MP', 'Madhya Pradesh', 'STATE'),
    ('IN-MH', 'Maharashtra', 'STATE'),
    ('IN-MN', 'Manipur', 'STATE'),
    ('IN-ML', 'Meghalaya', 'STATE'),
    ('IN-MZ', 'Mizoram', 'STATE'),
    ('IN-NL', 'Nagaland', 'STATE'),
    ('IN-OR', 'Odisha', 'STATE'),
    ('IN-PB', 'Punjab', 'STATE'),
    ('IN-RJ', 'Rajasthan', 'STATE'),
    ('IN-SK', 'Sikkim', 'STATE'),
    ('IN-TN', 'Tamil Nadu', 'STATE'),
    ('IN-TS', 'Telangana', 'STATE'),
    ('IN-TR', 'Tripura', 'STATE'),
    ('IN-UP', 'Uttar Pradesh', 'STATE'),
    ('IN-UT', 'Uttarakhand', 'STATE'),
    ('IN-WB', 'West Bengal', 'STATE'),
    ('IN-AN', 'Andaman and Nicobar Islands', 'UNION_TERRITORY'),
    ('IN-CH', 'Chandigarh', 'UNION_TERRITORY'),
    ('IN-DH', 'Dadra and Nagar Haveli and Daman and Diu', 'UNION_TERRITORY'),
    ('IN-DL', 'Delhi', 'UNION_TERRITORY'),
    ('IN-JK', 'Jammu and Kashmir', 'UNION_TERRITORY'),
    ('IN-LA', 'Ladakh', 'UNION_TERRITORY'),
    ('IN-LD', 'Lakshadweep', 'UNION_TERRITORY'),
    ('IN-PY', 'Puducherry', 'UNION_TERRITORY')
) AS v(code, name, state_type)
ON CONFLICT (country_id, normalized_name) DO NOTHING;

-- Well-known alternate spellings / former names so a typed query resolves to the canonical
-- state row. These are public common names, not fabricated identifiers.
INSERT INTO location_aliases (entity_type, entity_id, alias, normalized_alias)
SELECT 'STATE', s.id, a.alias, btrim(lower(regexp_replace(a.alias, '[^A-Za-z0-9 ]', '', 'g')))
FROM (VALUES
    ('Delhi', 'National Capital Territory of Delhi'),
    ('Delhi', 'NCT of Delhi'),
    ('Odisha', 'Orissa'),
    ('Tamil Nadu', 'Madras State'),
    ('Karnataka', 'Mysore State'),
    ('Chhattisgarh', 'C.G.'),
    ('Uttar Pradesh', 'U.P.'),
    ('Andhra Pradesh', 'A.P.'),
    ('Madhya Pradesh', 'M.P.'),
    ('Maharashtra', 'M.H.')
) AS a(canonical, alias)
JOIN states s ON s.normalized_name = btrim(lower(regexp_replace(a.canonical, '[^A-Za-z0-9 ]', '', 'g')))
ON CONFLICT (entity_type, entity_id, normalized_alias) DO NOTHING;
