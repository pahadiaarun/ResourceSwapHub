# Table Relationships

## Ownership chain

```text
users (1) ──< collections (N)
users (1) ──< listings (N) >── (1) books
collections (1) ──< listings (N)
listings (1) ──< listing_images (N) >── (1) media_assets
```

- A **user** owns collections, listings, uploaded media and refresh tokens.
- A **book** is catalogue data and is shared by every copy ever listed. It has no
  owner.
- A **collection** belongs to exactly one user. Listings may belong to a
  collection owned by the same user, enforced by the composite foreign key
  `listings(collection_id, owner_id) → collections(id, owner_id)`.
- Deleting a collection sets `listings.collection_id` to null without deleting
  the listings. Deleting a user cascades to their collections and listings.
- Deleting a book is restricted while any listing references it.

## Conversation chain

```text
users (1) ──< conversation_participants (N) >── (1) conversations
conversations (1) ──< messages (N) >── (1) users (sender)
conversations (1) ── (0..1) listings
```

- A **conversation** is the thread. `participants` is a join table, so the model
  can support more than two members later without schema changes; only `DIRECT`
  is accepted today.
- A direct conversation is anchored to one listing. If the listing is deleted,
  `listing_id` becomes null and the thread history survives.
- A **message** belongs to one conversation and one sender. Membership is not
  duplicated on the message; access is always checked through
  `conversation_participants`.
- `conversation_participants.last_read_message_id` references `messages` to
  compute unread counts. It is a pointer, not a duplicate of the message.

## Cardinality summary

| Parent | Child | Cardinality | On delete |
| --- | --- | --- | --- |
| users | refresh_tokens | 1:N | cascade |
| users | media_assets | 1:N | cascade |
| users | collections | 1:N | cascade |
| users | listings | 1:N | cascade |
| users | messages | 1:N | cascade |
| books | listings | 1:N | restrict |
| collections | listings | 1:N | set collection id null |
| listings | listing_images | 1:N | cascade |
| media_assets | listing_images | 1:N | restrict |
| listings | conversations | 1:N | set listing id null |
| conversations | conversation_participants | 1:N | cascade |
| conversations | messages | 1:N | cascade |
| messages | conversation_participants.last_read_message_id | 1:N | set null |

## Why these choices

- **Books shared, listings individual.** Duplicate catalogue rows would fragment
  search, so the generated `dedupe_key` collapses the same title/author/board/
  class/subject/edition/language combination.
- **History survives listing withdrawal.** Withdrawn listings are hidden from
  discovery but still readable by their owner, and existing threads retain their
  context.
- **Read markers instead of per-message receipts.** Unread counts only need the
  highest read message id, which keeps writes cheap and storage small.
- **No payment or reservation tables.** Version 1 explicitly excludes online
  payments and mandatory booking. The optimistic `listings.version` column is in
  place for a future reservation feature.
