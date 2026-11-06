# Phase 1 Feature Audit — ResourceSwapHub

Audit performed against the working tree (backend `resourceswaphub-backend`, frontend
`resourceswaphub-frontend`, database package `resourceswaphub-database`), production-generating
migrations `V1..V9`. Statuses use the definitions in the Phase 1 brief. "Test result" reflects the
state at audit time; later entries in this file are updated as implementation/testing lands.

Evidence paths are relative to the repository root.

## 0. System inventory (what exists)

| Layer | State | Evidence |
|---|---|---|
| Backend | Spring Boot modular monolith, modules `auth`, `user`, `profile`, `catalog`, `listing`, `collection`, `discovery`, `communication`, `location`, `institution`, `knowledge`, `notification` (chat-only), `shared/{media,security,api,error,web}`, `config` | `resourceswaphub-backend/src/main/java/com/bookbridge/*` |
| Database | PostgreSQL, additive Flyway migrations V1–V9 (identity/media, catalog/listings, communication, indexes, extended profiles/institutions, encryption sessions, knowledge topics, normalized locations, location discovery indexes) | `resourceswaphub-backend/src/main/resources/db/migration/` |
| Auth | Session-token auth (email/password + Google Sign-In), refresh tokens, `SecurityConfig` with explicit public routes | `com.bookbridge.auth`, `com.bookbridge.config.SecurityConfig` |
| Discovery | Server-side PostgreSQL full-text + category-free listing search with filters and pagination | `com.bookbridge.discovery.DiscoveryService`, `ListingRepository.discover` (ORDER BY created_at DESC only) |
| Location | Country → State → District → City with aliases, provenance, curated seed | `com.bookbridge.location`, migrations V8/V9 |
| Chat | Direct conversations, persistent messages, STOMP realtime, unread counts | `com.bookbridge.communication` |
| Media uploads | Local-storage object storage abstraction, magic-byte validation, 5 MB cap, purposes LISTING + AVATAR | `com.bookbridge.shared.media.*` |
| Tests | Spring Boot API integration tests: auth, chat flow, chat realtime, listing discovery, locations, profile records, crypto | `resourceswaphub-backend/src/test/java/com/bookbridge/` |
| Frontend | Next.js App Router + TypeScript, design-system components, PWA manifest/offline shell, public + app routes | `resourceswaphub-frontend/app`, `resourceswaphub-frontend/components` |
| Deployment | Vercel (frontend) + Spring Boot backend + Aiven PostgreSQL | repo README / `.github` workflows |

## 1. Feature-by-feature audit

| Feature | Existing status | Evidence | Test result | Required action |
|---|---|---|---|---|
| **A. Resource categories** | Missing | Listings require `book_id NOT NULL`; no category concept anywhere (`V2__catalog_and_listings.sql`; `listing/Listing.java`) | Not tested | Build: category directory + seed, nullable book for non-book resources, conditional metadata |
| **A. Listing metadata (title/description/level/board/publisher/edition/price/condition/location/images)** | Partial | Books carry title/author/ISBN/class/board/subject/edition/language; listings carry condition/price/city/locality/notes/status/images. No description, academic level, publication year, `state/city` free-text is legacy | Not tested | Build: extend listing model + forms; preserve book listings |
| **A. Discovery search by title/author/subject** | Partial-complete | Server-side FTS over book metadata (`DiscoveryService`, `ListingRepository.discover`); LIKE fallback present. Filters: subject/classLevel/board/priceType/condition/city/stateId/cityId/districtId/locality. Missing: sort options, price range, category filter, free-only quick filter | Not tested (re-test after change) | Extend with category/price range/sort; keep pagination |
| **B. Digital resources** | Missing | `MediaPurpose` only LISTING/AVATAR (images only); no file format/size/url fields; no download access control | Not tested | Build: medium field, digital asset flow, safe file handling, copyright gate |
| **B. Copyright/moderation/reporting** | Missing | No reports table, no admin role (users have no `role`), no takedown | Not tested | Build: uploader ownership confirmation, report flow, admin review/stand-down, audit trail |
| **C. Resource requests** | Missing | No request tables/API/screens | Not tested | Build end-to-end on existing location + chat |
| **D. Collections (create/edit/list own, public URL of one)** | Partial | CRUD exists (`collection/CollectionController`: create/get-by-id/patch/user-me-list); collection membership via `listing.collection_id` FK (single collection per listing), no publish/unpublish visibility, no public collection browse | Not tested | Build: collection_items join table (multi-collection), cover/category/level, public browse, item management that never deletes listings |
| **D. Bulk listing creation** | Missing | Frontend single-listing form only (`components/listing-form.tsx`) | Not tested | Build: multi-entry form + idempotent bulk endpoint |
| **E. Saved resources / saved collections** | Missing | No save tables/API/UI | Not tested | Build with unique constraint `(user_id, listing_id)` |
| **F. Notifications (persistent, notification centre)** | Missing | Only transactional STOMP chat event listener; no notifications table, no read state, no links | Not tested | Build: DB-backed notifications + read/read-all APIs + centre UI |
| **F. Resource alerts** | Missing | No alerts table or matching logic | Not tested | Build: alert CRUD + synchronous match on listing publish with dedupe constraint |
| **F. Request-expiry scheduling** | Missing | Nothing scheduled | Not tested | Build: Spring `@Scheduled` single-instance job; document hosting note |
| **G. Listing status model (AVAILABLE/UNAVAILABLE/WITHDRAWN/EXCHANGED)** | Complete (validated transitions in service; optimistic `version` column) | `listing/ListingService`, ck_listings_status check | Not tested this phase | Test + preserve |
| **G. My listings views** | Partial | `MyListingsController` ("library"); per-status screens exist in frontend library workspace | To re-test | Preserve; verify status grouping |
| **G. Lifecycle timestamps / status audit history** | Partial | `created_at`/`updated_at` only; no transition history | Not tested | Build `listing_status_history` |
| **G. Exchange confirmation + history** | Missing | EXCHANGED status exists but nothing records participants/history | Not tested | Build: exchange record + explicit two-party confirmation (non-blocking, separate from chat) |
| **Cross: auth/ownership/IDOR** | Complete in existing modules; must extend to all new endpoints | `SecurityConfig` public GETs for discovery/listings/locations; `.anyRequest().authenticated()` | Partial (existing e2e covers outsider chat rejection) | Enforce on every new endpoint + tests |
| **Cross: location FKs** | Complete | country/state/city ids with provenance on users/listings/collections (V8/V9) | Pass (era tests) | Reuse consistently everywhere new |
| **Cross: responsive/PWA** | Complete (manifest, offline shell, mobile bottom nav) | `app/manifest.ts`, `components/app-shell.tsx` | Verified visually in prior turn for existing screens | New screens must follow same patterns; verify at required widths |
| **Existing auth/chat/listing regression** | Complete | `AuthFlowIntegrationTest`, `ChatFlowIntegrationTest`, `ListingDiscoveryIntegrationTest` | Pass (will re-run as regression) | Preserve |

## 2. Key structural facts driving the design

1. **Listing is catalogue-anchored.** `listings.book_id` is `NOT NULL` — every listing currently
   **is** a book copy. Non-book resources (notes, papers, stationery) cannot be listed at all.
   The Phase 1 model extends `listings` (requested-resource marketplace) rather than adding a
   parallel `resources` table, keeping one discovery/index pipeline. See
   `docs/PHASE_1_DATABASE_CHANGES.md`.
2. **Collections are implicit.** A listing belongs to at most one collection via
   `listing.collection_id`; `collections` has no visibility/cover/category and
   `CollectionController` has no item-management endpoints beyond what create puts in the FK.
3. **Notifications don't exist.** Only wire-level chat events (`notification/ChatNotificationListener`).
4. **No admin/role concept.** `users` carries no role; moderation must introduce one additively.
5. **Storage exists but is images-only.** `shared/media` validates images (magic bytes), stores
   under `storage-dir`, serves `GET /api/v1/media/{id}` with public listing/avatar intent. Digital
   documents need a purpose + authorization layer before reuse.
6. **Discovery is server-side** (FTS in DB, paginated) — matching the Phase 1 requirement.

## 3. Test baseline at audit time

- `./mvnw test` suite green (auth, chat, discovery-with-locations, profile records, crypto).
- Frontend `npm run build` clean (`exit 0`), after the Phase 0 UI fixes.
- These will be re-run as regression gates after every backend phase and the final build.

**This baseline was wrong by the time the work landed and is corrected in §4.** The audit above is
a pre-implementation snapshot; the "Existing status" column still describes the tree *before* the
Phase 1 backend was written, so it now reads as historic.

## 4. Measured baseline after the Phase 1 backend landed (2026-11-03)

Every number here comes from an executed run, not from inspection.

| Gate | Command / target | Result |
|---|---|---|
| Pre-fix suite | `./mvnw -o test` on the uncommitted Phase 1 tree, scratch DB | **19/28 pass, 9 failures** |
| After the Jackson fix | same | **26/28 pass, 2 failures** |
| After the collection-attach fix | same, `bookbridge_phase1_check` (V1–V10 already applied) | **28/28 pass, BUILD SUCCESS** |
| Stage snapshot `b7d2cdc` | worktree at that commit, same DB | **28/28 pass, BUILD SUCCESS** |
| Stage snapshots `2be5404 f9e4d42 fca3212 d327168 83ec602` | worktree at each commit, `mvnw -o compile` | **compile OK for all five** |
| Final tree on an **empty** database | `./mvnw -o test` against a newly created `bookbridge_p1_final` | **V1–V10 applied from zero (now at v10), 28/28 pass, BUILD SUCCESS** |

### 4.1 Two defects the audit could not see, and what they taught

1. **`POST /api/v1/listings` returned 400 for every existing client.** The new
   `ownerRightsConfirmed` was declared as a primitive `boolean` record component, and Jackson 3
   refuses to bind a *missing* JSON value onto a primitive. Nine tests — every one that creates a
   listing — failed with "The request body could be read". Fixed by declaring it `Boolean` and
   reading it as `Boolean.TRUE.equals(...)` (absent means false), matching the pattern
   `ListingUpdateRequest` already used. Any new primitive field in a request record is an API
   break for clients that predate it.
2. **The create-time `collectionId` was dropped, and with it its authorization check.** The
   re-modelled create path no longer accepted a collection, so `ListingCreateRequest`'s field and
   the ownership test went quiet; a foreign collection id would have been accepted silently once
   the field returned. Restored as `CollectionService.attachForListingCreate`, which returns **403**
   when the caller names a collection that is not theirs — deliberately not the 404 that the
   collection-management endpoints use to hide foreign ids, because here the caller is asserting
   the id as their own. The stale assertion that the create response echoes `collection.id` was
   replaced with the correct source of truth: `GET /api/v1/collections/{id}` → `items[]`.

### 4.2 What is implemented but NOT tested

**Corrected in §5:** the blanket "no test coverage of its own" below was already wrong when it was
written — `Phase1MarketplaceIntegrationTest` (6 tests) covers the first four rows. The remaining rows
are genuinely uncovered.

| Area | Endpoints / behaviour with zero tests |
|---|---|
| Categories | Covered — `Phase1MarketplaceIntegrationTest` (directory, conditional metadata) |
| Digital resources | Covered — rights gate, DOCUMENT upload, download authorization |
| Bulk create | Covered — per-item isolation, `client_ref` replay, size cap |
| Collections | Multi-membership through `collection_items`, item add/remove, public browse, visibility |
| Requests | create/match/fulfil/cancel, expiry scheduler, chat linkage |
| Saved | save/unsave idempotency, notification on save and on status change |
| Alerts | dedupe on create, synchronous match on publish, notification fan-out |
| Notifications | list/unread/read/read-all persistence |
| Exchange | two-party confirm, exclusive participant slot, listing → EXCHANGED only when both confirm |
| Moderation | report create, admin role gate (`ModerationService`, not the filter chain), takedown/restore, audit rows |

Each row needs an integration test before the feature can be reported as passed.

### 4.3 Still missing entirely

- **Frontend**: no Phase 1 work at all — `lib/api.ts` and `lib/types.ts` have no methods or types
  for categories, requests, saved, alerts, notifications, exchanges or reports; no screens exist;
  the nav in `components/app-shell.tsx` has no new entries. Nothing in Phase 1 is user-reachable.
- **Docs**: only this audit file exists; the remaining Phase 1 documents are not written.

## 5. M0 baseline (measured, 2026-11-06)

`./mvnw -o test` against the local `bookbridge_test` database, V1–V10 applied: **34/34 pass, BUILD
SUCCESS** (the 28 pre-Phase-1 tests plus the 6 in `Phase1MarketplaceIntegrationTest`).

Two earlier claims in this file are now retired with evidence:

1. **There is no V10 checksum mismatch.** Flyway validates stored checksums on every test boot, so a
   mismatch would fail the whole suite at context startup; the suite starts and passes. Independently,
   `V10__phase1_marketplace_expansion.sql` has exactly one commit (`b7d2cdc`) and a clean working tree,
   so it was never edited after being applied.
2. **"No test coverage of its own" was stale** — see §4.2.

### 5.1 Two real defects the first M0 run exposed, and what they mean

The suite was **not** green when M0 began: 2 of the 6 Phase 1 tests failed. One was a test-fixture bug;
one was a product defect that made Feature B unusable on its main path.

1. **Creating a digital listing silently discarded the resource.** `ListingService.applyMetadata`
   assigned `medium` but never `externalUrl`, `digitalMedia` (with `fileFormat`/`fileSizeBytes`) or
   `ownerRightsConfirmed` — those assignments existed only in the update path. So `POST /listings` with
   `medium=EXTERNAL_LINK` produced a listing with **no link**, and `medium=DIGITAL_FILE` produced a
   listing with **no file**, whose `GET /listings/{id}/file` could only ever 404, and whose
   `owner_rights_confirmed` stayed `false` even though `RIGHTS_CONFIRMATION_REQUIRED` had to be
   satisfied to pass validation. The copyright audit field was therefore also untrustworthy. Fixed by
   moving the three assignments into `applyMetadata`, which both create and update call, and deleting
   the now-duplicated block from the update path so the two cannot drift apart again.
2. **A fake JPEG shorter than the validator's 12-byte floor** was rejected as `UNSUPPORTED_IMAGE`,
   failing the test rather than proving anything about the product. Replaced with a 12-byte
   SOI+APP0+JFIF header, which the magic-byte check accepts, so the assertion under it ("an image
   cannot be attached as a digital document") actually tests the rule.

The `ApiClient` harness also needed to stop assuming every response is JSON: a successful file
download returns the asset's own content type, and the unconditional `readTree` threw
`StreamReadException` on the binary body. It now parses only `application/json` responses.

### 5.2 Open defects carried into M2/M3

Recorded here so they are not lost; none is fixed yet.

| # | Defect | Location |
|---|---|---|
| D0 | `GET /media/{id}` serves an authorized DOCUMENT as `Cache-Control: public, max-age=31536000, immutable` — cacheable by shared caches | `shared/media/MediaController.java:57` |
| D1 | `GET /resource-requests` is `permitAll` and returns requester name + city | `config/SecurityConfig.java:51` |
| D2 | Admin takedown calls `setStatus(getStatus())` (no-op) and bypasses `transition()` → no history row, no saver notification | `moderation/ModerationService.java:106` |
| D3 | Saved-resources category filter runs after pagination; `totalElements`/`totalPages` are the unfiltered values | `saved/SavedService.java:88-98` |
| D4 | No path grants `ADMIN`, so report/review/takedown is unreachable | `user/User.java:81` |
| D5 | `demo.sql` still inserts the dropped `listings.collection_id` | `seeds/development/demo.sql:26` |
| D6 | `alertRepository.findAll()` per publish | `alerts/AlertService.java:115` |
| D7 | Alert dedupe silences a legitimate re-publish forever | `alerts/AlertService.java:122` |
| D8 | `notifications?unread=false` returns everything | `notification/NotificationService.java:64` |
| D9 | `ResourceRequestDto.conversationIds` always `List.of()` | `request/ResourceRequestService.java` |
| D10 | Expiry write path not asserted when the scheduler never fires | `request/RequestExpiryScheduler.java:22` |
| D11 | Saving a PRIVATE collection returns a bare 404 | `saved/SavedService.java` |
| D12 | Duplicate `import java.util.Locale` | `collection/CollectionService.java:5` |
| D13 | Request feed reuses `SellerDto` as `requester` | `request/ResourceRequestDto.java` |
| D14 | Alert fan-out runs inside the publish transaction (`BEFORE_COMMIT`) | `alerts/AlertService.java:102` |
| D15 | `listing_status_history` is written but has no reader or endpoint | `listing/ListingService.java:541` |

### 5.3 Environment reality at M0

- Local `bookbridge` is **unmigrated** (0 relations) — there is no local prod-shaped data, so the V10
  backfills (`:137` category, `:172` collection_items) have never run against real rows. This is the
  single biggest reason M1 rehearses on a restored production copy.
- `bookbridge_test`, `bookbridge_p1_final` and `bookbridge_phase1_check` all carry V1–V10 with matching
  checksums.
- API :8080 and web :3000 are stopped; Docker is unavailable, so `make db` + `make api` + `make web`
  is the only local path.
- Production is at **V9**; V10 has never run there, and `main` is ahead of `origin/main`.
