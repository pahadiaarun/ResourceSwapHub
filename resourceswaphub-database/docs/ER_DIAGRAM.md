# Entity Relationship Diagram

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "issues"
    USERS ||--o{ MEDIA_ASSETS : "uploads"
    USERS ||--o{ COLLECTIONS : "owns"
    USERS ||--o{ LISTINGS : "owns"
    USERS ||--o{ CONVERSATION_PARTICIPANTS : "joins"
    USERS ||--o{ MESSAGES : "sends"

    BOOKS ||--o{ LISTINGS : "is listed as"
    COLLECTIONS ||--o{ LISTINGS : "groups"

    LISTINGS ||--o{ LISTING_IMAGES : "has"
    MEDIA_ASSETS ||--o{ LISTING_IMAGES : "is referenced by"

    LISTINGS ||--o{ CONVERSATIONS : "starts"
    CONVERSATIONS ||--o{ CONVERSATION_PARTICIPANTS : "includes"
    CONVERSATIONS ||--o{ MESSAGES : "contains"
    MESSAGES ||--o{ CONVERSATION_PARTICIPANTS : "read marker"

    USERS {
        uuid id PK
        varchar email
        varchar password_hash
        varchar google_subject
        varchar display_name
        varchar avatar_url
        varchar city
        varchar locality
        timestamptz created_at
        timestamptz updated_at
    }
    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        timestamptz revoked_at
    }
    MEDIA_ASSETS {
        uuid id PK
        uuid owner_id FK
        varchar storage_key UK
        varchar content_type
        bigint size_bytes
        varchar purpose
    }
    BOOKS {
        uuid id PK
        varchar title
        varchar author
        varchar isbn
        smallint class_level
        varchar education_board
        varchar subject
        varchar edition
        varchar language
        text dedupe_key UK
        tsvector search_vector
    }
    COLLECTIONS {
        uuid id PK
        uuid owner_id FK
        varchar title
        varchar city
        varchar locality
    }
    LISTINGS {
        uuid id PK
        uuid owner_id FK
        uuid book_id FK
        uuid collection_id FK
        varchar condition
        varchar price_type
        numeric price
        varchar city
        varchar locality
        varchar status
        bigint version
    }
    LISTING_IMAGES {
        uuid id PK
        uuid listing_id FK
        uuid media_id FK
        smallint position
    }
    CONVERSATIONS {
        uuid id PK
        varchar type
        uuid listing_id FK
        varchar direct_key UK
        timestamptz last_message_at
    }
    CONVERSATION_PARTICIPANTS {
        uuid id PK
        uuid conversation_id FK
        uuid user_id FK
        varchar role
        bigint last_read_message_id FK
    }
    MESSAGES {
        bigint id PK
        uuid conversation_id FK
        uuid sender_id FK
        varchar body
        timestamptz created_at
    }
```

## Notes

- `listings.collection_id` is nullable. The composite foreign key
  `(collection_id, owner_id)` prevents a seller from attaching a listing to
  somebody else's collection at the database level.
- `books.dedupe_key` and `books.search_vector` are generated columns. They are
  derived data and must never be written by the application.
- `conversations.direct_key` is a canonical `listing:userA:userB` string with the
  two user UUIDs sorted. The unique index enforces one direct thread per listing
  and pair.
- `conversation_participants.last_read_message_id` is a soft pointer to the newest
  message the participant has read; it is `NULL` before the first read.
- Deletes cascade from users to their sessions, media, listings, collections,
  participants and messages. Books are protected with `ON DELETE RESTRICT`.
