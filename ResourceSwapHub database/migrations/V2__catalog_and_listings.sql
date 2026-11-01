-- V2: standardized catalogue, collections and book listings.

CREATE TABLE books (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    author          VARCHAR(255) NOT NULL,
    publisher       VARCHAR(255),
    isbn            VARCHAR(20),
    class_level     SMALLINT NOT NULL,
    education_board VARCHAR(20) NOT NULL,
    subject         VARCHAR(120) NOT NULL,
    edition         VARCHAR(80),
    language        VARCHAR(60) NOT NULL DEFAULT 'English',
    dedupe_key      TEXT GENERATED ALWAYS AS (
        lower(btrim(title)) || '|' ||
        lower(btrim(author)) || '|' ||
        lower(btrim(coalesce(publisher, ''))) || '|' ||
        class_level::text || '|' ||
        education_board || '|' ||
        lower(btrim(subject)) || '|' ||
        lower(btrim(coalesce(edition, ''))) || '|' ||
        lower(btrim(language))
    ) STORED,
    search_vector   TSVECTOR GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' ||
            coalesce(author, '') || ' ' ||
            coalesce(publisher, '') || ' ' ||
            coalesce(subject, '') || ' ' ||
            coalesce(isbn, '') || ' ' ||
            coalesce(edition, '') || ' ' ||
            language
        )
    ) STORED,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_books_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_books_author_not_blank CHECK (btrim(author) <> ''),
    CONSTRAINT ck_books_subject_not_blank CHECK (btrim(subject) <> ''),
    CONSTRAINT ck_books_language_not_blank CHECK (btrim(language) <> ''),
    CONSTRAINT ck_books_class_level CHECK (class_level BETWEEN 1 AND 12),
    CONSTRAINT ck_books_board CHECK (education_board IN ('CBSE', 'ICSE', 'IB', 'STATE_BOARD', 'OTHER')),
    CONSTRAINT ck_books_isbn CHECK (isbn IS NULL OR isbn ~ '^[0-9Xx-]{10,20}$')
);

CREATE UNIQUE INDEX uq_books_dedupe_key ON books (dedupe_key);
CREATE UNIQUE INDEX uq_books_isbn
    ON books (isbn)
    WHERE isbn IS NOT NULL AND btrim(isbn) <> '';

CREATE TRIGGER trg_books_updated_at
    BEFORE UPDATE ON books
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE collections (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           VARCHAR(120) NOT NULL,
    description     VARCHAR(1000),
    city            VARCHAR(80) NOT NULL,
    locality        VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_collections_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_collections_city_not_blank CHECK (btrim(city) <> ''),
    CONSTRAINT uq_collections_id_owner UNIQUE (id, owner_id)
);

CREATE INDEX idx_collections_owner ON collections (owner_id, created_at DESC);

CREATE TRIGGER trg_collections_updated_at
    BEFORE UPDATE ON collections
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE listings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    book_id         UUID NOT NULL REFERENCES books (id) ON DELETE RESTRICT,
    collection_id   UUID,
    condition       VARCHAR(20) NOT NULL,
    price_type      VARCHAR(20) NOT NULL,
    price           NUMERIC(10, 2),
    city            VARCHAR(80) NOT NULL,
    locality        VARCHAR(120),
    notes           VARCHAR(2000),
    status          VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_listings_collection_owner
        FOREIGN KEY (collection_id, owner_id)
        REFERENCES collections (id, owner_id)
        ON DELETE SET NULL (collection_id),
    CONSTRAINT ck_listings_condition CHECK (condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'FAIR', 'POOR')),
    CONSTRAINT ck_listings_price_type CHECK (price_type IN ('FREE', 'FIXED_PRICE', 'NEGOTIABLE')),
    CONSTRAINT ck_listings_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE', 'WITHDRAWN', 'EXCHANGED')),
    CONSTRAINT ck_listings_price CHECK (
        (price_type = 'FREE' AND price IS NULL)
        OR (price_type = 'FIXED_PRICE' AND price IS NOT NULL AND price > 0)
        OR (price_type = 'NEGOTIABLE' AND (price IS NULL OR price > 0))
    ),
    CONSTRAINT ck_listings_city_not_blank CHECK (btrim(city) <> ''),
    CONSTRAINT ck_listings_notes_length CHECK (notes IS NULL OR char_length(notes) <= 2000)
);

CREATE INDEX idx_listings_owner_status ON listings (owner_id, status, created_at DESC);
CREATE INDEX idx_listings_book ON listings (book_id);

CREATE TRIGGER trg_listings_updated_at
    BEFORE UPDATE ON listings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE listing_images (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id      UUID NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    media_id        UUID NOT NULL REFERENCES media_assets (id) ON DELETE RESTRICT,
    position        SMALLINT NOT NULL,
    CONSTRAINT uq_listing_images_media UNIQUE (listing_id, media_id),
    CONSTRAINT uq_listing_images_position UNIQUE (listing_id, position),
    CONSTRAINT ck_listing_images_position CHECK (position BETWEEN 0 AND 4)
);
