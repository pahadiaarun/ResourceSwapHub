# Query Catalog

The important reads and writes, with the indexes that support them. SQL is
abbreviated; the authoritative versions live in
`Backend/src/main/java/com/bookbridge/**/*Repository.java`.

## Discovery

### D1 — Filtered discovery feed

```sql
SELECT l.*
FROM listings l
JOIN books b ON b.id = l.book_id
WHERE l.status = 'AVAILABLE'
  AND (:hasQuery = false OR b.search_vector @@ to_tsquery('simple', :tsQuery))
  AND (:hasQuery = false OR lower(b.title) LIKE :likeQuery)
  AND (:subject IS NULL OR lower(b.subject) = lower(:subject))
  AND (:classLevel IS NULL OR b.class_level = :classLevel)
  AND (:board IS NULL OR b.education_board = :board)
  AND (:priceType IS NULL OR l.price_type = :priceType)
  AND (:condition IS NULL OR l.condition = :condition)
  AND (:city IS NULL OR lower(l.city) = lower(:city))
  AND (:locality IS NULL OR lower(l.locality) = lower(:locality))
ORDER BY l.created_at DESC, l.id DESC
LIMIT :size OFFSET :offset;
```

Indexes: `idx_books_search_vector`, `idx_listings_status_created`,
`idx_listings_status_city_locality_created`,
`idx_listings_status_price_condition_created`. Search combines FTS ranking with a
LIKE fallback for partial words.

### D2 — Discovery count

Same predicates as D1 with `COUNT(*)`; Spring Data derives it for page metadata.

### D3 — Taxonomy facets

```sql
SELECT DISTINCT subject FROM books ORDER BY subject;
SELECT DISTINCT class_level FROM books ORDER BY class_level;
```

Served by `idx_books_filters`.

## Catalogue

### C1 — Catalogue search

```sql
SELECT * FROM books
WHERE :q = '' OR search_vector @@ to_tsquery('simple', :prefixQuery)
ORDER BY title
LIMIT :limit;
```

### C2 — Deduplicated create

The service computes the same normalization as `dedupe_key`, looks up an
existing row, and returns it instead of inserting. The unique index is the final
guard against concurrent duplicates.

## Listings

### L1 — Listing detail

`SELECT … FROM listings JOIN FETCH book, owner, collection, images, media WHERE id = ?`
with `idx_listings_book`/PK. Withdrawn rows are filtered in the service for
non-owners.

### L2 — My listings

```sql
SELECT * FROM listings
WHERE owner_id = :ownerId
  AND (:status IS NULL OR status = :status)
ORDER BY created_at DESC
LIMIT :size OFFSET :offset;
```

Index: `idx_listings_owner_status`.

### L3 — Collection contents

```sql
SELECT * FROM listings
WHERE collection_id = :collectionId AND status <> 'WITHDRAWN'
ORDER BY created_at DESC;
```

### L4 — Status update

Single-row update with `version` check (`UPDATE … WHERE id = ? AND version = ?`).

## Communication

### M1 — Inbox

```sql
SELECT c.* FROM conversations c
JOIN conversation_participants p ON p.conversation_id = c.id
WHERE p.user_id = :userId
ORDER BY c.last_message_at DESC
LIMIT :size OFFSET :offset;
```

Indexes: `idx_conversation_participants_user`, `idx_conversations_last_message`.

### M2 — Latest message per conversation

Fetched in one batch for the inbox page using a window function over
`idx_messages_conversation_id_desc`, avoiding N+1 queries.

### M3 — Unread count

```sql
SELECT count(*)
FROM messages m
JOIN conversation_participants p
  ON p.conversation_id = m.conversation_id AND p.user_id = :userId
WHERE m.conversation_id = :conversationId
  AND (p.last_read_message_id IS NULL OR m.id > p.last_read_message_id)
  AND m.sender_id <> :userId;
```

Index: `idx_messages_conversation_id_desc`.

### M4 — Message page

```sql
SELECT * FROM messages
WHERE conversation_id = :id AND (:beforeId IS NULL OR id < :beforeId)
ORDER BY id DESC
LIMIT :size;
```

Returned ascending to the client with a `nextBefore` cursor.

### M5 — Send message

```sql
INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, ?);
-- trigger updates conversations.last_message_at
```

### M6 — Mark read

```sql
UPDATE conversation_participants
SET last_read_message_id = GREATEST(COALESCE(last_read_message_id, 0), :messageId)
WHERE conversation_id = :id AND user_id = :userId;
```

### M7 — Start conversation

Lookup by `direct_key`, insert conversation plus two participants in one
transaction. A unique violation is retried as an existing-thread lookup.

## Operations

### O1 — Health

`GET /actuator/health` performs a validation query against the pool.

### O2 — Flyway status

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history ORDER BY installed_rank;
```
