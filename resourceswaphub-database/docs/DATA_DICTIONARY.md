# Data Dictionary

Field-level meaning, allowed values and validation rules. Validation exists in
three layers: DTO annotations (request shape), entity/service rules (business
rules) and database constraints (last line of defence).

## Users and identity

| Field | Meaning | Rules |
| --- | --- | --- |
| `users.email` | Sign-in identifier | Required, ≤ 320 chars, valid email, stored lower-case, unique ignoring case |
| `users.password_hash` | BCrypt hash (cost 12) | Required for password accounts, null for Google-only accounts, never returned by the API |
| `users.google_subject` | Google account `sub` claim | Unique when present; stable across email changes |
| `users.display_name` | Public name | 2–80 chars, trimmed, non-blank |
| `users.avatar_url` | Profile photo | URL or `/api/v1/media/{id}`; remote Google photo allowed |
| `users.city` | City used for discovery | ≤ 80 chars; required together with locality to complete a profile |
| `users.locality` | Neighbourhood | ≤ 120 chars |
| `refresh_tokens.token_hash` | SHA-256 of the opaque refresh token | Raw token never stored; unique; 64 hex chars |
| `refresh_tokens.revoked_at` | Rotation/logout marker | Null while active |
| `media_assets.purpose` | Upload intent | `LISTING` (max 5 per listing) or `AVATAR` |
| `media_assets.content_type` | Validated image MIME | `image/jpeg`, `image/png`, `image/webp` |
| `media_assets.size_bytes` | File size | `1 … 5 MiB` |

## Catalogue

| Field | Meaning | Rules |
| --- | --- | --- |
| `books.title` | Book title | Required, ≤ 255 chars, non-blank |
| `books.author` | Author or institution | Required, ≤ 255 chars |
| `books.publisher` | Publisher | Optional, ≤ 255 chars |
| `books.isbn` | ISBN-10/13 | Optional, 10–20 chars of digits/`X`/hyphens, unique when present |
| `books.class_level` | Academic year | 1–12 |
| `books.education_board` | Curriculum | `CBSE`, `ICSE`, `IB`, `STATE_BOARD`, `OTHER` |
| `books.subject` | Subject name | Required, ≤ 120 chars |
| `books.edition` | Edition/year | Optional, ≤ 80 chars |
| `books.language` | Language of the copy | Required, ≤ 60 chars, default `English` |
| `books.dedupe_key` | Generated identity key | Lower-cased title/author/publisher/subject/edition/language + class + board |
| `books.search_vector` | Generated FTS document | Title, author, publisher, subject, ISBN, edition, language |

`education_board` input accepts `CBSE`, `ICSE`, `IB`, `STATE_BOARD`, `OTHER`,
case-insensitively and with spaces or hyphens (for example `state board`).

## Listings and collections

| Field | Meaning | Rules |
| --- | --- | --- |
| `collections.title` | Collection name | Required, 2–120 chars |
| `collections.description` | Free text | ≤ 1000 chars |
| `collections.city` / `locality` | Pickup area | City required |
| `listings.book_id` | Catalogue edition | Must exist |
| `listings.collection_id` | Optional grouping | Must belong to the same owner |
| `listings.condition` | Physical condition | `NEW`, `LIKE_NEW`, `GOOD`, `FAIR`, `POOR` |
| `listings.price_type` | Commercial model | `FREE`, `FIXED_PRICE`, `NEGOTIABLE` |
| `listings.price` | Asking price in INR | Free → null; fixed → required and > 0; negotiable → null or > 0 |
| `listings.city` | Pickup city | Required, ≤ 80 chars |
| `listings.locality` | Pickup locality | Optional, ≤ 120 chars |
| `listings.notes` | Seller notes | Optional, ≤ 2000 chars |
| `listings.status` | Availability | `AVAILABLE`, `UNAVAILABLE`, `WITHDRAWN`, `EXCHANGED` |
| `listings.version` | Optimistic lock | Managed by JPA (`@Version`), starts at 0 |
| `listing_images.position` | Display order | 0–4, unique per listing; max five images |

Status transitions (service-enforced):

```text
AVAILABLE   → UNAVAILABLE | WITHDRAWN | EXCHANGED
UNAVAILABLE → AVAILABLE   | WITHDRAWN | EXCHANGED
WITHDRAWN   → AVAILABLE   | UNAVAILABLE
EXCHANGED   → (terminal)
```

## Communication

| Field | Meaning | Rules |
| --- | --- | --- |
| `conversations.type` | Thread shape | Only `DIRECT` accepted in v1; join table is group-ready |
| `conversations.listing_id` | Context book | Required for direct threads; null if the listing is deleted |
| `conversations.direct_key` | Canonical uniqueness key | `{listingId}:{lowerUserId}:{higherUserId}`, unique |
| `conversations.last_message_at` | Inbox ordering | Set by trigger on message insert |
| `participants.role` | Relationship to listing | `OWNER` for the seller, `INTERESTED` for the enquirer |
| `participants.last_read_message_id` | Read pointer | Must be a message in the same conversation (service-checked) |
| `messages.body` | Message text | Trimmed, 1–4000 chars, non-blank |

## API enum labels

The API returns both raw enum values (`priceType`) and human labels
(`priceTypeLabel`) so clients never hard-code copy. Labels: `Free`,
`Fixed price`, `Negotiable`; conditions: `New`, `Like new`, `Good`, `Fair`,
`Poor`; statuses: `Available`, `Unavailable`, `Withdrawn`, `Exchanged`.

## Data that must never be stored

- Raw passwords or raw refresh tokens.
- Payment card data (out of scope for v1).
- Google access tokens; only the verified subject, email, name and picture URL
  are persisted.
- Uploaded file contents inside PostgreSQL; only metadata is stored.
