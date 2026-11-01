# Business Constraints

Rules that protect data integrity and user trust. Each rule names where it is
enforced: **DB** (constraint/index/trigger), **SVC** (service validation) and
**API** (DTO validation/authorization).

## Identity

| Rule | Enforcement |
| --- | --- |
| Email is unique ignoring case | DB `uq_users_email_lower`; SVC conflict check |
| Email is normalized to lower-case | SVC |
| Passwords are 8–72 characters and BCrypt-hashed | API + SVC |
| Google subject is unique | DB partial unique index |
| A password account cannot be taken over by a different Google subject | SVC `GOOGLE_ACCOUNT_MISMATCH` |
| Refresh tokens are single-use and rotate on refresh | SVC; replay revokes all user sessions |
| Logout revokes the presented refresh token | SVC |

## Profiles

| Rule | Enforcement |
| --- | --- |
| Name is 2–80 characters and non-blank | API + DB check |
| A profile is complete when city **and** locality are present | SVC `isProfileComplete` |
| Listing a book or starting a conversation requires a complete profile | SVC `PROFILE_INCOMPLETE` |
| Only the signed-in user can read/update their profile | API security |
| Avatar must be an image uploaded by the same user | SVC media ownership |

## Catalogue

| Rule | Enforcement |
| --- | --- |
| The same edition cannot be created twice | DB generated `dedupe_key` + unique index |
| ISBN, when supplied, is unique and structurally valid | DB unique partial index + check |
| Class is 1–12 | API + DB check |
| Board is one of the supported values | API + DB check |
| Title, author, subject and language are non-blank | DB checks |

Deduplication is intentional: the API returns the existing book when a matching
edition is submitted, so discovery never shows the same textbook twice.

## Listings

| Rule | Enforcement |
| --- | --- |
| Owner must have a complete profile | SVC |
| Book must exist | DB FK |
| Collection, when used, must belong to the same owner | DB composite FK + SVC |
| Free listings have no price | SVC + DB check |
| Fixed-price listings require a positive price | SVC + DB check |
| Negotiable listings may omit the price | DB check |
| At most five photos, each used once, in positions 0–4 | SVC + DB unique/check |
| Photos must be owned by the listing owner and uploaded for listings | SVC |
| Only the owner can edit, change status or withdraw a listing | SVC; others receive 404 to avoid leaking existence |
| Withdrawn listings are hidden from non-owners | SVC |
| Discovery only returns `AVAILABLE` listings | SVC query |
| `EXCHANGED` is terminal | SVC transition table |
| Concurrent edits use optimistic locking | DB `version` + JPA `@Version` |

## Communication

| Rule | Enforcement |
| --- | --- |
| A thread is unique per listing and pair of users | DB `uq_conversations_direct_key` |
| Users cannot message themselves about their own listing | SVC `OWN_LISTING` |
| Conversations can only start from an `AVAILABLE` listing | SVC |
| Only participants can read the thread, messages, send or mark read | SVC membership check + API auth |
| Non-participants receive 404, not 403, to avoid confirming a thread | SVC |
| Message body is trimmed, non-blank and ≤ 4000 characters | API + SVC + DB check |
| Read pointer must reference a message in the same conversation | SVC |
| Unread count cannot go negative | Comparison against `last_read_message_id` |
| WebSocket subscriptions are restricted to the user's own queue | STOMP interceptor |
| Messages are written through REST only; STOMP SEND is rejected | STOMP interceptor |

## Retention and deletion

| Rule | Enforcement |
| --- | --- |
| Deleting a user removes their sessions, media metadata, listings and messages | DB cascades |
| Deleting a listing keeps its conversations (listing context becomes null) | DB `ON DELETE SET NULL` |
| Deleting a book is blocked while listings reference it | DB `ON DELETE RESTRICT` |
| Uploaded files are not deleted automatically with metadata | Application storage policy (future cleanup job) |

## Explicitly out of scope for v1

- Online payments, refunds and payouts.
- Mandatory reservations or allocation of a specific physical copy.
- Group conversations (schema is ready; API rejects them).
- Moderation workflows and notifications beyond chat unread state.
