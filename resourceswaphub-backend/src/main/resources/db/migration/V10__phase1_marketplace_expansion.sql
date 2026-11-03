-- V10: Phase 1 marketplace expansion.
--
-- Additive only: no row is deleted or rewritten except the two documented
-- backfills below (listings -> TEXTBOOKS category, listings.collection_id ->
-- collection_items). Migrations V1..V9 are untouched.
--
-- Contents
--   1. users.role                      (admin/moderation)
--   2. resource_categories            (+ seed of the Phase 1 catalogue)
--   3. media_assets purpose DOCUMENT   (digital resource files)
--   4. listings extension              (conditional metadata, digital fields,
--                                       search vector, client_ref idempotency)
--   5. collections extension           + collection_items join table
--   6. resource_requests               (with request conversations on V3 table)
--   7. saved_resources / saved_collections
--   8. resource_alerts                 (fingerprint-deduped)
--   9. notifications
--  10. listing_status_history
--  11. exchanges                       (two-party confirmation)
--  12. resource_reports / moderation_actions

-- ---------------------------------------------------------------------------
-- 1. users.role
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));

-- ---------------------------------------------------------------------------
-- 2. resource_categories
-- ---------------------------------------------------------------------------
CREATE TABLE resource_categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            VARCHAR(60) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(500),
    sort_order      SMALLINT NOT NULL DEFAULT 0,
    allows_physical BOOLEAN NOT NULL DEFAULT true,
    allows_digital  BOOLEAN NOT NULL DEFAULT false,
    book_required   BOOLEAN NOT NULL DEFAULT false,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_resource_categories_slug UNIQUE (slug),
    CONSTRAINT ck_resource_categories_name CHECK (btrim(name) <> '')
);

CREATE TRIGGER trg_resource_categories_updated_at
    BEFORE UPDATE ON resource_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO resource_categories
    (slug, name, description, sort_order, allows_physical, allows_digital, book_required)
VALUES
    ('textbooks',                'Textbooks',                          'Course and curriculum textbooks for school or college.',                10, true,  false, true),
    ('reference-books',          'Reference books',                    'Dictionaries, encyclopedias and other reference volumes.',              20, true,  false, true),
    ('competitive-exam-books',   'Competitive exam preparation books', 'JEE, NEET, UPSC, CAT and similar preparation books.',                   30, true,  false, true),
    ('school-books',             'School books',                       'Class 1-12 school syllabus books.',                                     40, true,  false, true),
    ('college-books',            'College books',                      'Degree, engineering and university course books.',                      50, true,  false, true),
    ('academic-notes',           'Academic notes',                     'Handwritten, typed or scanned study notes.',                            60, true,  true,  false),
    ('study-guides',             'Study guides',                       'Summaries, guides and revision material.',                              70, true,  true,  false),
    ('previous-papers',          'Previous-year question papers',      'Past exam and board question papers.',                                  80, true,  true,  false),
    ('lab-materials',            'Practical and laboratory materials', 'Lab manuals, practical files and experiment sheets.',                   90, true,  true,  false),
    ('stationery',               'Educational stationery',             'Geometry boxes, calculators and other educational stationery.',        100, true,  false, false),
    ('other-resources',          'Other educational resources',        'Any other educational resource that helps students learn.',            110, true,  true,  false)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. media_assets: DOCUMENT purpose (digital resource files)
-- ---------------------------------------------------------------------------
ALTER TABLE media_assets DROP CONSTRAINT ck_media_purpose;
ALTER TABLE media_assets
    ADD CONSTRAINT ck_media_purpose CHECK (purpose IN ('LISTING', 'AVATAR', 'DOCUMENT'));

-- ---------------------------------------------------------------------------
-- 4. listings extension
-- ---------------------------------------------------------------------------
ALTER TABLE listings ALTER COLUMN book_id DROP NOT NULL;
ALTER TABLE listings ALTER COLUMN condition DROP NOT NULL;

ALTER TABLE listings ADD COLUMN category_id         UUID REFERENCES resource_categories (id) ON DELETE RESTRICT;
ALTER TABLE listings ADD COLUMN title               VARCHAR(255);
ALTER TABLE listings ADD COLUMN subject             VARCHAR(120);
ALTER TABLE listings ADD COLUMN academic_level      VARCHAR(80);
ALTER TABLE listings ADD COLUMN board               VARCHAR(20);
ALTER TABLE listings ADD COLUMN author              VARCHAR(255);
ALTER TABLE listings ADD COLUMN publisher           VARCHAR(255);
ALTER TABLE listings ADD COLUMN edition             VARCHAR(80);
ALTER TABLE listings ADD COLUMN publication_year    SMALLINT;
ALTER TABLE listings ADD COLUMN medium              VARCHAR(20);
ALTER TABLE listings ADD COLUMN digital_media_id    UUID REFERENCES media_assets (id) ON DELETE RESTRICT;
ALTER TABLE listings ADD COLUMN external_url        VARCHAR(2048);
ALTER TABLE listings ADD COLUMN file_format         VARCHAR(20);
ALTER TABLE listings ADD COLUMN file_size_bytes     BIGINT;
ALTER TABLE listings ADD COLUMN owner_rights_confirmed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE listings ADD COLUMN moderation_state    VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE listings ADD COLUMN client_ref          VARCHAR(64);
ALTER TABLE listings ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    to_tsvector('simple',
        coalesce(title, '') || ' ' ||
        coalesce(notes, '') || ' ' ||
        coalesce(subject, '') || ' ' ||
        coalesce(author, '') || ' ' ||
        coalesce(publisher, '') || ' ' ||
        coalesce(edition, '') || ' ' ||
        coalesce(academic_level, '')
    )
) STORED;

ALTER TABLE listings
    ADD CONSTRAINT ck_listings_medium CHECK (medium IS NULL OR medium IN ('PHYSICAL', 'DIGITAL_FILE', 'EXTERNAL_LINK'));
ALTER TABLE listings
    ADD CONSTRAINT ck_listings_moderation_state CHECK (moderation_state IN ('NONE', 'RESTRICTED', 'REMOVED'));
-- A listing is either a catalogue-anchored book copy or carries its own title.
ALTER TABLE listings
    ADD CONSTRAINT ck_listings_title_present CHECK (
        book_id IS NOT NULL
        OR (title IS NOT NULL AND btrim(title) <> '' AND char_length(title) <= 255)
    );
ALTER TABLE listings
    ADD CONSTRAINT ck_listings_title_only_without_book CHECK (book_id IS NULL OR title IS NULL);
ALTER TABLE listings
    ADD CONSTRAINT ck_listings_external_url CHECK (external_url IS NULL OR external_url ~ '^https://\S+$');
ALTER TABLE listings
    ADD CONSTRAINT ck_listings_publication_year CHECK (publication_year IS NULL OR publication_year BETWEEN 1900 AND 2100);
ALTER TABLE listings
    ADD CONSTRAINT ck_listings_digital_fields CHECK (
        (digital_media_id IS NULL OR medium = 'DIGITAL_FILE')
        AND (external_url IS NULL OR medium = 'EXTERNAL_LINK')
        AND (file_format IS NULL OR digital_media_id IS NOT NULL)
        AND (file_size_bytes IS NULL OR digital_media_id IS NOT NULL)
    );

-- Backfill: every existing listing is a book copy; the default physical book
-- category keeps discovery filters meaningful for legacy rows.
UPDATE listings
   SET category_id = (SELECT id FROM resource_categories WHERE slug = 'textbooks')
 WHERE category_id IS NULL;

CREATE INDEX idx_listings_category_status ON listings (category_id, status, created_at DESC);
CREATE INDEX idx_listings_search_vector ON listings USING GIN (search_vector);
CREATE UNIQUE INDEX uq_listings_owner_client_ref
    ON listings (owner_id, client_ref)
    WHERE client_ref IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. collections extension + collection_items
-- ---------------------------------------------------------------------------
ALTER TABLE collections ADD COLUMN cover_media_id    UUID REFERENCES media_assets (id) ON DELETE SET NULL;
ALTER TABLE collections ADD COLUMN category_id       UUID REFERENCES resource_categories (id) ON DELETE SET NULL;
ALTER TABLE collections ADD COLUMN academic_level    VARCHAR(80);
ALTER TABLE collections ADD COLUMN institution       VARCHAR(200);
ALTER TABLE collections ADD COLUMN visibility        VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE collections ADD COLUMN published_at      TIMESTAMPTZ;

ALTER TABLE collections
    ADD CONSTRAINT ck_collections_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'));

CREATE TABLE collection_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id UUID NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    listing_id    UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (collection_id, listing_id)
);

CREATE INDEX idx_collection_items_listing ON collection_items (listing_id);

-- Backfill: move the legacy single-collection link into the join table before
-- the column is removed. Identical (collection, listing) pairs collapse.
INSERT INTO collection_items (collection_id, listing_id)
SELECT listing.collection_id, listing.id
  FROM listings listing
 WHERE listing.collection_id IS NOT NULL
ON CONFLICT DO NOTHING;

-- The old composite FK only existed to support `listings.collection_id`;
-- multi-collection membership replaces it.
ALTER TABLE listings DROP CONSTRAINT fk_listings_collection_owner;
ALTER TABLE listings DROP COLUMN collection_id;
ALTER TABLE collections DROP CONSTRAINT uq_collections_id_owner;

-- ---------------------------------------------------------------------------
-- 6. resource_requests
-- ---------------------------------------------------------------------------
CREATE TABLE resource_requests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(2000),
    category_id         UUID REFERENCES resource_categories (id) ON DELETE SET NULL,
    subject             VARCHAR(120),
    author              VARCHAR(255),
    edition             VARCHAR(80),
    academic_level      VARCHAR(80),
    preferred_condition VARCHAR(20),
    price_min           NUMERIC(10, 2),
    price_max           NUMERIC(10, 2),
    free_only           BOOLEAN NOT NULL DEFAULT false,
    country_id          UUID REFERENCES countries (id) ON DELETE SET NULL,
    state_id            UUID REFERENCES states (id) ON DELETE SET NULL,
    city_id             UUID REFERENCES cities (id) ON DELETE SET NULL,
    city_name           VARCHAR(80) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    expires_at          TIMESTAMPTZ NOT NULL,
    expired_at          TIMESTAMPTZ,
    fulfilled_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_vector       tsvector GENERATED ALWAYS AS (
        to_tsvector('simple',
            coalesce(title, '') || ' ' ||
            coalesce(description, '') || ' ' ||
            coalesce(subject, '') || ' ' ||
            coalesce(author, '')
        )
    ) STORED,
    CONSTRAINT ck_resource_requests_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_resource_requests_description CHECK (description IS NULL OR char_length(description) <= 2000),
    CONSTRAINT ck_resource_requests_condition CHECK (
        preferred_condition IS NULL
        OR preferred_condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')
    ),
    CONSTRAINT ck_resource_requests_status CHECK (status IN ('OPEN', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_resource_requests_price_range CHECK (
        (price_min IS NULL AND price_max IS NULL)
        OR (price_min IS NULL AND price_max > 0)
        OR (price_min > 0 AND price_max IS NULL)
        OR (price_min > 0 AND price_min <= price_max)
    ),
    CONSTRAINT ck_resource_requests_expires CHECK (expires_at > created_at)
);

CREATE INDEX idx_resource_requests_status_expires
    ON resource_requests (status, expires_at);
CREATE INDEX idx_resource_requests_requester
    ON resource_requests (requester_id, created_at DESC);
CREATE INDEX idx_resource_requests_location
    ON resource_requests (state_id, city_id) WHERE status = 'OPEN';
CREATE INDEX idx_resource_requests_search_vector
    ON resource_requests USING GIN (search_vector);
CREATE INDEX idx_resource_requests_category
    ON resource_requests (category_id) WHERE status = 'OPEN';

CREATE TRIGGER trg_resource_requests_updated_at
    BEFORE UPDATE ON resource_requests
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Request conversations reuse the V3 tables: a conversation targets either a
-- listing or a resource request, never both.
ALTER TABLE conversations ADD COLUMN request_id UUID REFERENCES resource_requests (id) ON DELETE SET NULL;
ALTER TABLE conversations DROP CONSTRAINT ck_conversations_direct_listing;
ALTER TABLE conversations
    ADD CONSTRAINT ck_conversations_target CHECK (listing_id IS NOT NULL OR request_id IS NOT NULL);
CREATE INDEX idx_conversations_request ON conversations (request_id);

-- ---------------------------------------------------------------------------
-- 7. saved resources / saved collections
-- ---------------------------------------------------------------------------
CREATE TABLE saved_resources (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, listing_id)
);

CREATE INDEX idx_saved_resources_listing ON saved_resources (listing_id);

CREATE TABLE saved_collections (
    user_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    collection_id UUID NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, collection_id)
);

-- ---------------------------------------------------------------------------
-- 8. resource_alerts
-- ---------------------------------------------------------------------------
CREATE TABLE resource_alerts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    keyword        VARCHAR(120),
    category_id    UUID REFERENCES resource_categories (id) ON DELETE CASCADE,
    subject        VARCHAR(120),
    academic_level VARCHAR(80),
    state_id       UUID REFERENCES states (id) ON DELETE CASCADE,
    city_id        UUID REFERENCES cities (id) ON DELETE CASCADE,
    free_only      BOOLEAN NOT NULL DEFAULT false,
    enabled        BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fingerprint    TEXT GENERATED ALWAYS AS (
        coalesce(keyword, '') || '|' ||
        coalesce(category_id::text, '') || '|' ||
        coalesce(subject, '') || '|' ||
        coalesce(academic_level, '') || '|' ||
        coalesce(state_id::text, '') || '|' ||
        coalesce(city_id::text, '') || '|' ||
        free_only::text
    ) STORED,
    CONSTRAINT ck_resource_alerts_keyword CHECK (keyword IS NULL OR btrim(keyword) <> '')
);

CREATE UNIQUE INDEX uq_resource_alerts_user_fingerprint
    ON resource_alerts (user_id, fingerprint);
CREATE INDEX idx_resource_alerts_user ON resource_alerts (user_id, created_at DESC);

CREATE TRIGGER trg_resource_alerts_updated_at
    BEFORE UPDATE ON resource_alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 9. notifications
-- ---------------------------------------------------------------------------
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(30) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500),
    link_path  VARCHAR(300),
    entity_id  UUID,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_type CHECK (type IN
        ('REQUEST_RESPONSE', 'REQUEST_FULFILLED', 'SAVED_LISTING_UNAVAILABLE', 'LISTING_ALERT_MATCH'))
);

CREATE INDEX idx_notifications_user_created
    ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread
    ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;

-- Save-handle notifications dedupe on (user, type, entity, unread) in the
-- listener; a fresh transition can notify again after the first was read.

-- ---------------------------------------------------------------------------
-- 10. listing_status_history
-- ---------------------------------------------------------------------------
CREATE TABLE listing_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id  UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    source      VARCHAR(30) NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_status_history_source CHECK (source IN
        ('USER', 'EXCHANGE', 'MODERATION', 'SYSTEM'))
);

CREATE INDEX idx_listing_status_history_listing
    ON listing_status_history (listing_id, created_at);

-- ---------------------------------------------------------------------------
-- 11. exchanges (explicit two-party confirmation)
-- ---------------------------------------------------------------------------
CREATE TABLE exchanges (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id             UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    owner_id               UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    participant_id         UUID REFERENCES users (id) ON DELETE CASCADE,
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    owner_confirmed_at     TIMESTAMPTZ,
    participant_confirmed_at TIMESTAMPTZ,
    confirmed_at           TIMESTAMPTZ,
    conversation_id        UUID REFERENCES conversations (id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exchanges_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

-- One active exchange per listing; a cancelled record can be replaced.
CREATE UNIQUE INDEX uq_exchanges_active_listing
    ON exchanges (listing_id)
    WHERE status <> 'CANCELLED';

CREATE TRIGGER trg_exchanges_updated_at
    BEFORE UPDATE ON exchanges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------------
-- 12. moderation: reports + audit trail
-- ---------------------------------------------------------------------------
CREATE TABLE resource_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id  UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason      VARCHAR(20) NOT NULL,
    details     VARCHAR(1000),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reviewed_by UUID REFERENCES users (id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_reports_reason CHECK (reason IN
        ('COPYRIGHT', 'INAPPROPRIATE', 'SPAM', 'MISLEADING', 'OTHER')),
    CONSTRAINT ck_reports_status CHECK (status IN ('OPEN', 'REVIEWED', 'ACTIONED', 'DISMISSED'))
);

CREATE INDEX idx_resource_reports_listing ON resource_reports (listing_id, created_at DESC);
CREATE INDEX idx_resource_reports_status ON resource_reports (status, created_at DESC);

CREATE TABLE moderation_actions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    actor_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    action     VARCHAR(30) NOT NULL,
    reason     VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_moderation_action CHECK (action IN
        ('RESTRICT', 'REMOVE', 'RESTORE'))
);

CREATE INDEX idx_moderation_actions_listing
    ON moderation_actions (listing_id, created_at DESC);
