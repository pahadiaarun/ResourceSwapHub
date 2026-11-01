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

The GIN index uses the built-in `simple` configuration. No `pg_trgm` or
`unaccent` extension is required, which keeps the schema portable to Aiven.

## Discovery

| Index | Definition | Serves |
| --- | --- | --- |
| `idx_listings_status_created` | `(status, created_at DESC)` | Default "recently added, available" feed and pagination |
| `idx_listings_status_city_locality_created` | `(status, lower(city), lower(locality), created_at DESC)` | Location filters with recent-first ordering |
| `idx_listings_status_price_condition_created` | `(status, price_type, condition, created_at DESC)` | Free/paid and condition facets |
| `idx_listings_owner_status` | `(owner_id, status, created_at DESC)` | "My listings" tab with optional status filter |
| `idx_listings_book` | `(book_id)` | Listing lookup by catalogue edition; FK support |
| `idx_listing_images_listing_position` | `(listing_id, position)` | Ordered gallery load |

The discovery query always constrains `status = 'AVAILABLE'` for guests and
non-owners, so `status` leads the composite indexes. `lower(city)` and
`lower(locality)` match the service's case-insensitive filters.

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
