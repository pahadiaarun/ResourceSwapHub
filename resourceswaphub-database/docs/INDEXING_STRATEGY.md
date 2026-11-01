# Indexing Strategy

Indexes are chosen from the actual query paths in `DiscoveryService`,
`ChatService` and the repositories. Every index below has a named consumer;
unused indexes should be removed in a future migration.

## Catalogue

| Index | Definition | Serves |
| --- | --- | --- |
| `uq_books_dedupe_key` | unique B-tree on generated `dedupe_key` | Deduplication on create; exact canonical lookup |
| `uq_books_isbn` | unique partial on `isbn` where non-blank | Prevent duplicate ISBNs without blocking ISBN-less books |
| `idx_books_search_vector` | GIN on `search_vector` | `websearch_to_tsquery` / prefix search on title, author, publisher, subject |
| `idx_books_filters` | B-tree `(class_level, education_board, subject)` | Filter-only discovery and catalogue facets |

Book search uses the built-in `simple` text-search configuration. Fuzzy place and institution
matching uses `pg_trgm` (enabled in V5), so the schema needs that extension available on the host.

## Discovery

| Index | Definition | Serves |
| --- | --- | --- |
| `idx_listings_status_created` | `(status, created_at DESC)` | Default "recently added, available" feed and pagination |
| `idx_listings_status_city_locality_created` | `(status, lower(city), lower(locality), created_at DESC)` | Legacy text filters with recent-first ordering |
| `idx_listings_state_discover` | partial `(state_id, city_id) WHERE status = 'AVAILABLE' AND state_id IS NOT NULL` | Filter by state, and by city within that state (V9) |
| `idx_listings_city_discover` | partial `(city_id) WHERE status = 'AVAILABLE' AND city_id IS NOT NULL` | Filter by standardized city (V9) |
| `idx_listings_status_price_condition_created` | `(status, price_type, condition, created_at DESC)` | Free/paid and condition facets |
| `idx_listings_owner_status` | `(owner_id, status, created_at DESC)` | "My listings" tab with optional status filter |
| `idx_listings_book` | `(book_id)` | Listing lookup by catalogue edition; FK support |
| `idx_listing_images_listing_position` | `(listing_id, position)` | Ordered gallery load |

The discovery query always constrains `status = 'AVAILABLE'` for guests and
non-owners, so `status` leads the composite indexes. The two location indexes added in V9 are
partial for the same reason: they only exist to serve the browse path, and the id filters need no
expression column because the directory already stores one canonical value per row.

## Communication

| Index | Definition | Serves |
| --- | --- | --- |
| `uq_conversations_direct_key` | unique on `direct_key` | One thread per listing and pair; race-safe start |
| `idx_conversations_last_message` | `(last_message_at DESC)` | Inbox ordering (with participant join) |
| `idx_conversations_listing` | `(listing_id)` | Listing → threads; FK support |
| `idx_conversation_participants_user` | `(user_id, conversation_id)` | Inbox lookup and membership checks |
| unique `(conversation_id, user_id)` | B-tree | Prevent duplicate membership |
| `idx_messages_conversation_id_desc` | `(conversation_id, id DESC)` | Latest page and "before id" pagination |
| `idx_refresh_tokens_user_active` | partial `(user_id, expires_at)` | Session validation and revoke-all |
| `idx_media_assets_owner` | `(owner_id, created_at DESC)` | Owner media listing |

## Location directory

| Index | Definition | Serves |
| --- | --- | --- |
| `uq_countries_iso` / `uq_countries_normalized` | unique B-tree | ISO lookup, name resolution, one row per place |
| `uq_states_country_name` / `uq_districts_state_name` / `uq_cities_state_name` | unique on `(parent_id, normalized_name)` | Parent-scoped identity: same name in two states stays two rows, and the importer's idempotent upsert key |
| `idx_states_country`, `idx_districts_state`, `idx_cities_state`, `idx_cities_district` | B-tree on the FK | Cascading select lists, FK support |
| `idx_cities_normalized` | B-tree on `normalized_name` (V9) | "is this name used by exactly one row in the directory?" during backfill |
| `idx_*_normalized_trgm` | GIN `gin_trgm_ops` on each `normalized_name` | Spelling-tolerant `LIKE '%q%'` / `similarity()` ranking in the selects |
| `idx_location_aliases_normalized` | GIN `gin_trgm_ops` | Alias search ("Poona", "Orissa") without a second table scan |
| `idx_location_aliases_entity` | `(entity_type, entity_id)` | Alias lookup for a resolved row |
| `idx_users_city`, `idx_users_state` | partial `(city_id)` / `(state_id)` where not null | Backfill candidate scans, "readers near me" |
| `idx_collections_state`, `idx_collections_city` | partial where not null | Grouped sets filtered by place |

Directory reads always start from a parent id, so the composite uniqueness constraints double as the
covering index for the cascading selects; the trigram indexes only matter for the typed query in the
combobox, where exact and alias matches are ranked ahead of fuzzy neighbours.

## Operating notes

- Keep pagination keyset-friendly: discovery orders by `created_at DESC, id DESC`
  and messages by `id DESC`; both can use the indexes above without a sort.
- Unread counts are computed with `COUNT(*)` over messages newer than the
  participant's read pointer. If inbox volume grows, add a covering index on
  `(conversation_id, id)` including `sender_id` after measuring.
- Run `EXPLAIN (ANALYZE, BUFFERS)` on the discovery query after large seed loads;
  refresh statistics with `ANALYZE listings, books` when distribution changes.
- Indexes add write cost. Any new index must name the query it serves and appear
  in this document in the same pull request.
