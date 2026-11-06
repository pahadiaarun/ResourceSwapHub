package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The M2 must-fix set: document cache hygiene (D0), request-feed authentication (D1),
 * takedown audit and saver notification (D2), honest saved pagination (D3), the requester
 * card shape (D13) and a request body that does not demand every optional field (D16).
 * It also locks the two 500s the first M2 run exposed: the report endpoints mapped lazy
 * associations after their transaction closed (D17), and reading a request expired lazily
 * inside a read-only transaction (D18).
 */
class Phase1MustFixIntegrationTest extends ApiIntegrationTest {

    private static final byte[] JPEG_HEADER = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01
    };

    private UUID categoryId(String slug) {
        return jdbc.queryForObject("SELECT id FROM resource_categories WHERE slug = ?", UUID.class, slug);
    }

    private static String id(ApiClient.ApiCall call) {
        return call.data().path("id").asString();
    }

    private static String header(ApiClient.ApiCall call, String name) {
        return call.headers().firstValue(name).orElse("(absent)");
    }

    private Map<String, Object> physicalPayload(UUID categoryId, String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("categoryId", categoryId.toString());
        payload.put("title", title);
        payload.put("medium", "PHYSICAL");
        payload.put("priceType", "FREE");
        payload.put("condition", "GOOD");
        payload.put("subject", "Mathematics");
        return payload;
    }

    private UUID createPhysicalListing(Session seller, UUID categoryId, String title) {
        var created = api.post("/api/v1/listings", physicalPayload(categoryId, title), seller.token());
        assertThat(created.status()).as("create %s: %s", title, created.body()).isEqualTo(201);
        return UUID.fromString(id(created));
    }

    private String statusOf(UUID listingId) {
        return jdbc.queryForObject("SELECT status FROM listings WHERE id = ?", String.class, listingId);
    }

    // --------------------------------------------------------------------- D0

    @Test
    void onlyPublicImagesGetAPublicCacheEntry() {
        Session owner = registerWithProfile("Doc Owner", "Pune", "Kothrud");
        UUID notes = categoryId("academic-notes");

        var upload = api.postMultipart("/api/v1/media", owner.token(), "notes.pdf", "application/pdf",
                "%PDF-1.4\nTest notes document\n".getBytes(StandardCharsets.UTF_8),
                Map.of("purpose", "DOCUMENT"));
        assertThat(upload.status()).as("pdf upload: %s", upload.body()).isEqualTo(201);
        UUID documentId = UUID.fromString(id(upload));

        var document = api.get("/api/v1/media/" + documentId, owner.token());
        assertThat(document.status()).as("document fetch: %s", document.body()).isEqualTo(200);
        assertThat(header(document, "cache-control"))
                .as("a permission-checked document must not sit in a shared cache")
                .contains("no-store")
                .doesNotContain("public")
                .doesNotContain("immutable");

        var image = api.postMultipart("/api/v1/media", owner.token(), "photo.jpg", "image/jpeg",
                JPEG_HEADER, Map.of("purpose", "LISTING"));
        assertThat(image.status()).as("image upload: %s", image.body()).isEqualTo(201);
        var guestImage = api.get("/api/v1/media/" + id(image), null);
        assertThat(guestImage.status()).as("guest image").isEqualTo(200);
        assertThat(header(guestImage, "cache-control"))
                .as("listing photos stay cacheable")
                .contains("public")
                .contains("immutable");

        Map<String, Object> payload = physicalPayload(notes, "Physics notes pdf");
        payload.put("medium", "DIGITAL_FILE");
        payload.remove("condition");
        payload.put("ownerRightsConfirmed", true);
        payload.put("digitalMediaId", documentId.toString());
        var listing = api.post("/api/v1/listings", payload, owner.token());
        assertThat(listing.status()).as("digital listing: %s", listing.body()).isEqualTo(201);

        var download = api.get("/api/v1/listings/" + id(listing) + "/file", owner.token());
        assertThat(download.status()).as("owner download").isEqualTo(200);
        assertThat(header(download, "cache-control")).doesNotContain("public");
    }

    // --------------------------------------------------------------------- D1, D13, D16

    @Test
    void theRequestFeedIsSignedInOnlyAndDescribesTheRequesterSparingly() {
        Session requester = registerWithProfile("Seeker", "Pune", "Kothrud");
        UUID notes = categoryId("academic-notes");

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Looking for a Statistics textbook");
        payload.put("description", "Class 12 syllabus, any condition.");
        payload.put("categoryId", notes.toString());
        payload.put("academicLevel", "Class 12");
        var created = api.post("/api/v1/resource-requests", payload, requester.token());
        assertThat(created.status()).as("create request without freeOnly: %s", created.body()).isEqualTo(201);
        String requestId = id(created);

        assertThat(api.get("/api/v1/resource-requests", null).status())
                .as("anonymous feed").isIn(401, 403);
        assertThat(api.get("/api/v1/resource-requests/" + requestId, null).status())
                .as("anonymous request detail").isIn(401, 403);
        assertThat(api.get("/api/v1/users/me/resource-requests", null).status())
                .as("anonymous own requests").isIn(401, 403);

        var feed = api.get("/api/v1/resource-requests", requester.token());
        assertThat(feed.status()).as("signed-in feed: %s", feed.body()).isEqualTo(200);
        assertThat(feed.data().path("content").size()).isEqualTo(1);

        var detail = api.get("/api/v1/resource-requests/" + requestId, requester.token());
        assertThat(detail.status()).as("signed-in detail: %s", detail.body()).isEqualTo(200);
        var card = detail.data().path("requester");
        assertThat(card.path("displayName").asString()).isEqualTo("Seeker");
        assertThat(card.path("city").asString()).isEqualTo("Pune");
        assertThat(card.path("id").asString()).isEqualTo(requester.id().toString());
        assertThat(card.has("locality")).as("a request card carries no micro-location").isFalse();
        assertThat(card.has("email")).as("no seller account fields leak through").isFalse();

        // Listing browse is unchanged by the new rule.
        Session seller = registerWithProfile("Seller", "Pune", "Kothrud");
        UUID bookId = createBook(uniqueTitle("NCERT Mathematics"), "Mathematics", 10, "CBSE");
        UUID listingId = createListing(seller, bookId);
        assertThat(api.get("/api/v1/listings/" + listingId, null).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/discovery/listings?q=Mathematics", null).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/categories", null).status()).isEqualTo(200);
    }

    // --------------------------------------------------------------------- D2

    @Test
    void takedownRecordsTheTransitionAndWarnsSaversThenRestoreLeavesRelistingToTheOwner() {
        Session seller = registerWithProfile("Listing Owner", "Pune", "Kothrud");
        Session saver = registerWithProfile("Saver", "Nagpur", "Dharampeth");
        Session reporter = registerWithProfile("Reporter", "Pune", "Baner");
        Session admin = register("Moderator");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.id());

        UUID bookId = createBook(uniqueTitle("Selina Concise Mathematics"), "Mathematics", 10, "ICSE");
        UUID listingId = createListing(seller, bookId);
        assertThat(api.post("/api/v1/listings/" + listingId + "/save", null, saver.token()).status())
                .as("save").isEqualTo(200);

        var report = api.post("/api/v1/reports", Map.of(
                "listingId", listingId.toString(),
                "reason", "COPYRIGHT",
                "details", "Uploaded as notes but it is a scanned textbook."), reporter.token());
        assertThat(report.status()).as("report: %s", report.body()).isEqualTo(201);

        var takedown = api.post("/api/v1/admin/listings/" + listingId + "/moderation",
                Map.of("action", "REMOVE", "reason", "Copyrighted scan"), admin.token());
        assertThat(takedown.status()).as("takedown: %s", takedown.body()).isEqualTo(200);
        assertThat(takedown.data().path("moderationState").asString()).isEqualTo("REMOVED");

        assertThat(api.get("/api/v1/listings/" + listingId, null).status())
                .as("hidden from guests").isEqualTo(404);
        assertThat(api.get("/api/v1/listings/" + listingId, saver.token()).status())
                .as("hidden from students").isEqualTo(404);
        assertThat(statusOf(listingId)).as("takedown moves the listing out of circulation")
                .isEqualTo("UNAVAILABLE");

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT from_status, to_status, source, changed_by FROM listing_status_history "
                        + "WHERE listing_id = ? AND source = 'MODERATION'", listingId);
        assertThat(history).as("the takedown is in the listing's own status history").hasSize(1);
        assertThat(history.get(0).get("from_status")).isEqualTo("AVAILABLE");
        assertThat(history.get(0).get("to_status")).isEqualTo("UNAVAILABLE");
        assertThat(history.get(0).get("changed_by")).isEqualTo(admin.id());

        var inbox = api.get("/api/v1/users/me/notifications", saver.token());
        assertThat(inbox.status()).as("inbox: %s", inbox.body()).isEqualTo(200);
        List<String> warnedAbout = new ArrayList<>();
        inbox.data().path("content").forEach(item -> {
            if ("SAVED_LISTING_UNAVAILABLE".equals(item.path("type").asString())) {
                warnedAbout.add(item.path("entityId").asString());
            }
        });
        assertThat(warnedAbout).as("exactly one notice, for this listing").containsExactly(listingId.toString());

        var restore = api.post("/api/v1/admin/listings/" + listingId + "/moderation",
                Map.of("action", "RESTORE", "reason", "Owner proved the rights"), admin.token());
        assertThat(restore.status()).as("restore: %s", restore.body()).isEqualTo(200);
        assertThat(restore.data().path("moderationState").asString()).isEqualTo("NONE");
        assertThat(api.get("/api/v1/listings/" + listingId, saver.token()).status())
                .as("visible again once the state clears").isEqualTo(200);
        assertThat(statusOf(listingId))
                .as("the owner decides when it is back on the market")
                .isEqualTo("UNAVAILABLE");
    }

    // ----------------------------------------------------------------- D17, D18

    @Test
    void reportAndReviewRoundTripKeepsTheirLazyAssociationsLoaded() {
        Session seller = registerWithProfile("Seller", "Pune", "Kothrud");
        Session reporter = registerWithProfile("Reporter", "Pune", "Baner");
        Session reader = register("Plain Reader");
        Session admin = register("Moderator");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.id());

        UUID bookId = createBook(uniqueTitle("Amar Chanda Mama"), "Bengali", 8, "ICSE");
        UUID listingId = createListing(seller, bookId);

        var report = api.post("/api/v1/reports", Map.of(
                "listingId", listingId.toString(),
                "reason", "SPAM",
                "details", "Duplicate of another listing."), reporter.token());
        assertThat(report.status()).as("report: %s", report.body()).isEqualTo(201);
        String reportId = id(report);
        assertThat(report.data().path("listingTitle").asString()).startsWith("Amar Chanda Mama");
        assertThat(report.data().path("reporterName").asString()).isEqualTo("Reporter");
        assertThat(report.data().path("status").asString()).isEqualTo("OPEN");

        assertThat(api.get("/api/v1/admin/reports", reader.token()).status())
                .as("signed in is not the same as admin").isEqualTo(403);

        var queue = api.get("/api/v1/admin/reports?status=OPEN", admin.token());
        assertThat(queue.status()).as("admin queue: %s", queue.body()).isEqualTo(200);
        assertThat(queue.data().path("content").size()).isEqualTo(1);

        var resolved = api.patch("/api/v1/admin/reports/" + reportId,
                Map.of("status", "ACTIONED"), admin.token());
        assertThat(resolved.status()).as("resolve: %s", resolved.body()).isEqualTo(200);
        assertThat(resolved.data().path("status").asString()).isEqualTo("ACTIONED");

        var reopened = api.patch("/api/v1/admin/reports/" + reportId,
                Map.of("status", "OPEN"), admin.token());
        assertThat(reopened.status()).as("reopen refused: %s", reopened.body()).isEqualTo(400);
        assertThat(reopened.body().path("error").path("code").asString()).isEqualTo("INVALID_STATUS");
    }

    @Test
    void viewingAnOutOfDateRequestExpiresItInsteadOfFailing() {
        Session requester = registerWithProfile("Seeker", "Pune", "Kothrud");
        UUID notes = categoryId("academic-notes");

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", "Need a Physics lab manual");
        payload.put("categoryId", notes.toString());
        payload.put("expiresInDays", 1);
        var created = api.post("/api/v1/resource-requests", payload, requester.token());
        assertThat(created.status()).as("create: %s", created.body()).isEqualTo(201);
        UUID requestId = UUID.fromString(id(created));
        assertThat(created.data().path("status").asString()).isEqualTo("OPEN");

        jdbc.update("UPDATE resource_requests SET created_at = now() - interval '2 days', "
                + "expires_at = now() - interval '1 minute' WHERE id = ?", requestId);

        var detail = api.get("/api/v1/resource-requests/" + requestId, requester.token());
        assertThat(detail.status()).as("detail after the deadline: %s", detail.body()).isEqualTo(200);
        assertThat(detail.data().path("status").asString()).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM resource_requests WHERE id = ?",
                String.class, requestId)).isEqualTo("EXPIRED");
    }

    // --------------------------------------------------------------------- D3

    @Test
    void savedResourcesFilterByCategoryWithoutContradictingTheirOwnTotals() {
        Session seller = registerWithProfile("Seller", "Pune", "Kothrud");
        Session saver = registerWithProfile("Saver", "Nagpur", "Dharampeth");
        UUID notes = categoryId("academic-notes");
        UUID papers = categoryId("previous-papers");

        List<UUID> saved = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            saved.add(createPhysicalListing(seller, notes, "Revision notes set " + i));
        }
        saved.add(createPhysicalListing(seller, papers, "Board paper 2024"));
        for (UUID listingId : saved) {
            assertThat(api.post("/api/v1/listings/" + listingId + "/save", null, saver.token()).status())
                    .as("save " + listingId).isEqualTo(200);
        }

        var unfiltered = api.get("/api/v1/users/me/saved-resources", saver.token());
        assertThat(unfiltered.status()).as("saved list: %s", unfiltered.body()).isEqualTo(200);
        assertThat(unfiltered.data().path("content").size()).isEqualTo(4);
        assertThat(unfiltered.data().path("totalElements").asInt()).isEqualTo(4);

        var firstPage = api.get("/api/v1/users/me/saved-resources?categoryId=" + notes + "&page=0&size=2",
                saver.token());
        assertThat(firstPage.status()).as("filtered page: %s", firstPage.body()).isEqualTo(200);
        assertThat(firstPage.data().path("content").size()).isEqualTo(2);
        assertThat(firstPage.data().path("totalElements").asInt())
                .as("totals describe the filtered set, not every saved row").isEqualTo(3);
        assertThat(firstPage.data().path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.data().path("hasNext").asBoolean()).isTrue();
        firstPage.data().path("content").forEach(item ->
                assertThat(item.path("listing").path("category").path("slug").asString())
                        .isEqualTo("academic-notes"));

        var secondPage = api.get("/api/v1/users/me/saved-resources?categoryId=" + notes + "&page=1&size=2",
                saver.token());
        assertThat(secondPage.data().path("content").size()).isEqualTo(1);
        assertThat(secondPage.data().path("hasNext").asBoolean()).isFalse();

        var otherCategory = api.get("/api/v1/users/me/saved-resources?categoryId=" + papers, saver.token());
        assertThat(otherCategory.data().path("content").size()).isEqualTo(1);
        assertThat(otherCategory.data().path("content").get(0)
                .path("listing").path("category").path("slug").asString()).isEqualTo("previous-papers");
    }
}
