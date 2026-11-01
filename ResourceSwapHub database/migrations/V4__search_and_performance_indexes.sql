-- V4: search and discovery indexes.
-- The GIN index powers PostgreSQL full-text search without extensions, keeping
-- the schema portable across Aiven and other managed PostgreSQL providers.

CREATE INDEX idx_books_search_vector ON books USING GIN (search_vector);
CREATE INDEX idx_books_filters ON books (class_level, education_board, subject);

CREATE INDEX idx_listings_status_created
    ON listings (status, created_at DESC);

CREATE INDEX idx_listings_status_city_locality_created
    ON listings (status, lower(city), lower(locality), created_at DESC);

CREATE INDEX idx_listings_status_price_condition_created
    ON listings (status, price_type, condition, created_at DESC);

CREATE INDEX idx_listing_images_listing_position
    ON listing_images (listing_id, position);

CREATE INDEX idx_conversations_listing ON conversations (listing_id);
