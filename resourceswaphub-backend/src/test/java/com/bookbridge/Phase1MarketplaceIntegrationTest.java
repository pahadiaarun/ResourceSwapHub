package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Feature group A (categories, listing metadata, discovery), B (digital files and the download
 * guards) and D (bulk publish) of the Phase 1 expansion.
 */
class Phase1MarketplaceIntegrationTest extends ApiIntegrationTest {

    private UUID categoryId(String slug) {
        return jdbc.queryForObject("SELECT id FROM resource_categories WHERE slug = ?", UUID.class, slug);
    }

    private Map<String, Object> freeFormPayload(UUID categoryId, String title, String medium, String priceType) {
        Map<String, Object> payload = new HashMap<>();
        if (categoryId != null) {
            payload.put("categoryId", categoryId.toString());
        }
        payload.put("title", title);
        payload.put("medium", medium);
        payload.put("priceType", priceType);
        if ("FIXED_PRICE".equals(priceType)) {
            payload.put("price", 120);
        }
        payload.put("subject", "Mathematics");
        payload.put("academicLevel", "Class 10");
        payload.put("locality", "Kothrud");
        return payload;
    }

    @Test
    void categoryDirectoryIsPublicAndCarriesItsRules() {
        var directory = api.get("/api/v1/categories", null);
        assertThat(directory.status()).as("categories: %s", directory.body()).isEqualTo(200);
        assertThat(directory.data().size()).isGreaterThanOrEqualTo(11);

        Map<String, Map<String, Object>> bySlug = new HashMap<>();
        directory.data().forEach(node -> bySlug.put(node.path("slug").asString(), Map.of(
                "digital", node.path("allowsDigital").asBoolean(),
                "book", node.path("bookRequired").asBoolean())));

        assertThat(bySlug).containsKeys("textbooks", "academic-notes", "previous-papers", "stationery");
        assertThat(bySlug.get("textbooks").get("book")).isEqualTo(true);
        assertThat(bySlug.get("textbooks").get("digital")).isEqualTo(false);
        assertThat(bySlug.get("academic-notes").get("digital")).isEqualTo(true);
        assertThat(bySlug.get("stationery").get("digital")).isEqualTo(false);
    }

    @Test
    void freeFormListingNeedsCategoryTitleAndCondition() {
        Session seller = registerWithProfile("Notes Seller", "Pune", "Kothrud");
        UUID notes = categoryId("academic-notes");
        UUID textbooks = categoryId("textbooks");

        var noCategory = api.post("/api/v1/listings",
                freeFormPayload(null, "Algebra revision notes", "PHYSICAL", "FREE"), seller.token());
        assertThat(noCategory.status()).as("no category: %s", noCategory.body()).isEqualTo(400);
        assertThat(noCategory.body().path("error").path("code").asString()).isEqualTo("CATEGORY_REQUIRED");

        var wrongCategory = api.post("/api/v1/listings",
                freeFormPayload(textbooks, "Algebra revision notes", "PHYSICAL", "FREE"), seller.token());
        assertThat(wrongCategory.status()).isEqualTo(400);
        assertThat(wrongCategory.body().path("error").path("code").asString()).isEqualTo("BOOK_REQUIRED");

        var noTitle = api.post("/api/v1/listings",
                freeFormPayload(notes, "  ", "PHYSICAL", "FREE"), seller.token());
        assertThat(noTitle.status()).isEqualTo(400);
        assertThat(noTitle.body().path("error").path("code").asString()).isEqualTo("TITLE_REQUIRED");

        var noCondition = api.post("/api/v1/listings",
                freeFormPayload(notes, "Algebra revision notes", "PHYSICAL", "FREE"), seller.token());
        assertThat(noCondition.status()).as("physical needs condition: %s", noCondition.body()).isEqualTo(400);
        assertThat(noCondition.body().path("error").path("code").asString()).isEqualTo("CONDITION_REQUIRED");

        Map<String, Object> payload = freeFormPayload(notes, "Algebra revision notes", "PHYSICAL", "FIXED_PRICE");
        payload.put("condition", "LIKE_NEW");
        payload.put("externalUrl", "https://example.com/notes");
        var conflict = api.post("/api/v1/listings", payload, seller.token());
        assertThat(conflict.status()).as("physical cannot carry a link: %s", conflict.body()).isEqualTo(400);
        assertThat(conflict.body().path("error").path("code").asString()).isEqualTo("DIGITAL_CONFLICT");

        payload.remove("externalUrl");
        var created = api.post("/api/v1/listings", payload, seller.token());
        assertThat(created.status()).as("create: %s", created.body()).isEqualTo(201);
        assertThat(created.data().path("medium").asString()).isEqualTo("PHYSICAL");
        assertThat(created.data().path("title").asString()).isEqualTo("Algebra revision notes");
        assertThat(created.data().path("category").path("slug").asString()).isEqualTo("academic-notes");
        assertThat(created.data().path("book").isNull() || created.data().path("book").isMissingNode()).isTrue();
        assertThat(created.data().path("status").asString()).isEqualTo("AVAILABLE");
        assertThat(created.data().path("price").asDouble()).isEqualTo(120.0);
    }

    @Test
    void externalLinkListingIsGatedOnRightsAndASecureUrl() {
        Session seller = registerWithProfile("Link Sharer", "Pune", "Kothrud");
        UUID papers = categoryId("previous-papers");

        var noRights = api.post("/api/v1/listings",
                freeFormPayload(papers, "CBSE 2024 paper set", "EXTERNAL_LINK", "FREE"), seller.token());
        assertThat(noRights.status()).as("rights gate: %s", noRights.body()).isEqualTo(400);
        assertThat(noRights.body().path("error").path("code").asString())
                .isEqualTo("RIGHTS_CONFIRMATION_REQUIRED");

        Map<String, Object> payload = freeFormPayload(papers, "CBSE 2024 paper set", "EXTERNAL_LINK", "FREE");
        payload.put("ownerRightsConfirmed", true);
        var insecure = api.post("/api/v1/listings", payload, seller.token());
        assertThat(insecure.status()).as("missing url: %s", insecure.body()).isEqualTo(400);
        assertThat(insecure.body().path("error").path("code").asString()).isEqualTo("INVALID_EXTERNAL_URL");

        payload.put("externalUrl", "http://example.com/papers");
        var http = api.post("/api/v1/listings", payload, seller.token());
        assertThat(http.status()).as("plain http refused: %s", http.body()).isEqualTo(400);
        // Bean validation on the record rejects the shape before the service sees it, so the code
        // here is VALIDATION_ERROR rather than the service-level INVALID_EXTERNAL_URL.
        assertThat(http.body().path("error").path("code").asString()).isEqualTo("VALIDATION_ERROR");

        payload.put("externalUrl", "https://example.com/papers");
        payload.put("condition", "GOOD");
        var withCondition = api.post("/api/v1/listings", payload, seller.token());
        assertThat(withCondition.status()).as("digital has no condition: %s", withCondition.body()).isEqualTo(400);
        assertThat(withCondition.body().path("error").path("code").asString()).isEqualTo("DIGITAL_CONFLICT");

        payload.remove("condition");
        var created = api.post("/api/v1/listings", payload, seller.token());
        assertThat(created.status()).as("create link: %s", created.body()).isEqualTo(201);
        assertThat(created.data().path("medium").asString()).isEqualTo("EXTERNAL_LINK");
        assertThat(created.data().path("externalUrl").asString()).isEqualTo("https://example.com/papers");
        assertThat(created.data().path("ownerRightsConfirmed").asBoolean()).isTrue();
    }

    @Test
    void digitalFileListingGuardsEveryDownload() {
        Session seller = registerWithProfile("Pdf Owner", "Pune", "Kothrud");
        Session stranger = registerWithProfile("Other Student", "Nagpur", "Dharampeth");
        UUID notes = categoryId("academic-notes");
        UUID stationery = categoryId("stationery");

        var uploaded = api.postMultipart("/api/v1/media", seller.token(), "notes.pdf", "application/pdf",
                "%PDF-1.4\nTest notes document\n".getBytes(StandardCharsets.UTF_8),
                Map.of("purpose", "DOCUMENT"));
        assertThat(uploaded.status()).as("pdf upload: %s", uploaded.body()).isEqualTo(201);
        UUID documentId = UUID.fromString(uploaded.data().path("id").asString());

        // The copyright gate is checked before the file presence, so a digital listing missing both
        // reports the rights problem first.
        var missingBoth = api.post("/api/v1/listings",
                freeFormPayload(notes, "Physics notes pdf", "DIGITAL_FILE", "FREE"), seller.token());
        assertThat(missingBoth.status()).as("rights gate: %s", missingBoth.body()).isEqualTo(400);
        assertThat(missingBoth.body().path("error").path("code").asString())
                .isEqualTo("RIGHTS_CONFIRMATION_REQUIRED");

        Map<String, Object> payload = freeFormPayload(notes, "Physics notes pdf", "DIGITAL_FILE", "FREE");
        payload.put("ownerRightsConfirmed", true);
        var missingFile = api.post("/api/v1/listings", payload, seller.token());
        assertThat(missingFile.status()).as("file required: %s", missingFile.body()).isEqualTo(400);
        assertThat(missingFile.body().path("error").path("code").asString()).isEqualTo("DIGITAL_FILE_REQUIRED");

        payload.put("digitalMediaId", documentId.toString());
        Map<String, Object> fileWithoutRights = new HashMap<>(payload);
        fileWithoutRights.remove("ownerRightsConfirmed");
        var noRights = api.post("/api/v1/listings", fileWithoutRights, seller.token());
        assertThat(noRights.status()).as("rights gate: %s", noRights.body()).isEqualTo(400);
        assertThat(noRights.body().path("error").path("code").asString())
                .isEqualTo("RIGHTS_CONFIRMATION_REQUIRED");

        payload.put("ownerRightsConfirmed", true);
        Map<String, Object> stationeryDigital = freeFormPayload(stationery, "Geometry box scan",
                "DIGITAL_FILE", "FREE");
        stationeryDigital.put("ownerRightsConfirmed", true);
        var wrongCategory = api.post("/api/v1/listings", stationeryDigital, seller.token());
        assertThat(wrongCategory.status()).as("digital not allowed: %s", wrongCategory.body()).isEqualTo(400);
        assertThat(wrongCategory.body().path("error").path("code").asString()).isEqualTo("DIGITAL_NOT_ALLOWED");

        var foreignFile = api.post("/api/v1/listings", payload, stranger.token());
        assertThat(foreignFile.status()).as("cannot share another user's file: %s", foreignFile.body())
                .isIn(400, 403);

        var imageAsDocument = api.postMultipart("/api/v1/media", seller.token(), "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
                        'J', 'F', 'I', 'F', 0x00, 0x01},
                Map.of("purpose", "LISTING"));
        assertThat(imageAsDocument.status()).as("image upload: %s", imageAsDocument.body()).isEqualTo(201);
        UUID imageId = UUID.fromString(imageAsDocument.data().path("id").asString());

        Map<String, Object> imagePayload = new HashMap<>(payload);
        imagePayload.put("digitalMediaId", imageId.toString());
        var imageRejected = api.post("/api/v1/listings", imagePayload, seller.token());
        assertThat(imageRejected.status()).as("images are not documents: %s", imageRejected.body()).isEqualTo(400);
        assertThat(imageRejected.body().path("error").path("code").asString()).isEqualTo("INVALID_DIGITAL_FILE");

        var created = api.post("/api/v1/listings", payload, seller.token());
        assertThat(created.status()).as("create digital: %s", created.body()).isEqualTo(201);
        assertThat(created.data().path("medium").asString()).isEqualTo("DIGITAL_FILE");
        assertThat(created.data().path("digitalFile").path("id").asString()).isEqualTo(documentId.toString());
        UUID listingId = UUID.fromString(created.data().path("id").asString());

        var ownerDownload = api.get("/api/v1/listings/" + listingId + "/file", seller.token());
        assertThat(ownerDownload.status()).as("owner download").isEqualTo(200);
        assertThat(ownerDownload.headers().firstValue("content-disposition").orElse(""))
                .contains("attachment").contains("notes.pdf");

        var signedInStranger = api.get("/api/v1/listings/" + listingId + "/file", stranger.token());
        assertThat(signedInStranger.status()).as("available file for a signed-in student").isEqualTo(200);

        var anonymousDownload = api.get("/api/v1/listings/" + listingId + "/file", null);
        assertThat(anonymousDownload.status()).as("anonymous download refused").isIn(401, 403);

        var anonymousDocument = api.get("/api/v1/media/" + documentId, null);
        assertThat(anonymousDocument.status()).as("anonymous document refused").isIn(401, 403);

        api.patch("/api/v1/listings/" + listingId + "/status", Map.of("status", "WITHDRAWN"), seller.token());
        var withdrawn = api.get("/api/v1/listings/" + listingId + "/file", stranger.token());
        assertThat(withdrawn.status()).as("withdrawn file hidden from others").isEqualTo(404);
        var ownerStillSeesIt = api.get("/api/v1/listings/" + listingId + "/file", seller.token());
        assertThat(ownerStillSeesIt.status()).as("owner keeps access to their own file").isEqualTo(200);
    }

    @Test
    void bulkPublishIsolatesFailuresAndRejectsAReplayedClientRef() {
        Session seller = registerWithProfile("Bulk Seller", "Pune", "Kothrud");
        UUID notes = categoryId("academic-notes");

        Map<String, Object> first = freeFormPayload(notes, uniqueTitle("Handwritten notes"), "PHYSICAL", "FREE");
        first.put("condition", "GOOD");
        first.put("clientRef", "ref-" + UUID.randomUUID());
        Map<String, Object> broken = freeFormPayload(notes, uniqueTitle("Notes without condition"),
                "PHYSICAL", "FREE");
        Map<String, Object> third = freeFormPayload(notes, uniqueTitle("Lab manual"), "PHYSICAL", "NEGOTIABLE");
        third.put("condition", "LIKE_NEW");
        third.put("price", 90);

        var bulk = api.post("/api/v1/listings/bulk", Map.of("items", List.of(first, broken, third)),
                seller.token());
        assertThat(bulk.status()).as("bulk: %s", bulk.body()).isEqualTo(200);
        assertThat(bulk.data().size()).isEqualTo(3);
        assertThat(bulk.data().get(0).path("success").asBoolean()).isTrue();
        assertThat(bulk.data().get(0).path("clientRef").asString()).isEqualTo(first.get("clientRef"));
        assertThat(bulk.data().get(1).path("success").asBoolean()).isFalse();
        assertThat(bulk.data().get(1).path("errorCode").asString()).isEqualTo("CONDITION_REQUIRED");
        assertThat(bulk.data().get(2).path("success").asBoolean()).isTrue();

        var mine = api.get("/api/v1/users/me/listings?size=48", seller.token());
        assertThat(mine.data().path("totalElements").asInt()).isEqualTo(2);

        var replay = api.post("/api/v1/listings/bulk", Map.of("items", List.of(first)), seller.token());
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.data().get(0).path("success").asBoolean()).isFalse();
        assertThat(replay.data().get(0).path("errorCode").asString()).isEqualTo("DUPLICATE_CLIENT_REF");

        var empty = api.post("/api/v1/listings/bulk", Map.of("items", List.of()), seller.token());
        assertThat(empty.status()).as("empty batch: %s", empty.body()).isEqualTo(400);
        assertThat(empty.body().path("error").path("code").asString()).isEqualTo("BULK_EMPTY");

        List<Map<String, Object>> tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) {
            Map<String, Object> item = freeFormPayload(notes, uniqueTitle("Notes copy"), "PHYSICAL", "FREE");
            item.put("condition", "GOOD");
            tooMany.add(item);
        }
        var oversized = api.post("/api/v1/listings/bulk", Map.of("items", tooMany), seller.token());
        assertThat(oversized.status()).as("oversized batch: %s", oversized.body()).isEqualTo(400);
        assertThat(oversized.body().path("error").path("code").asString()).isEqualTo("BULK_TOO_LARGE");

        var singleReplay = api.post("/api/v1/listings", first, seller.token());
        assertThat(singleReplay.status()).as("replayed ref: %s", singleReplay.body()).isEqualTo(409);
        assertThat(singleReplay.body().path("error").path("code").asString()).isEqualTo("DUPLICATE_CLIENT_REF");
    }

    @Test
    void discoveryFiltersByCategoryAndPriceAndSorts() {
        Session seller = registerWithProfile("Multi Listing Seller", "Pune", "Kothrud");
        UUID notes = categoryId("academic-notes");
        UUID guides = categoryId("study-guides");

        Map<String, Object> cheap = freeFormPayload(notes, uniqueTitle("Cheap notes"), "PHYSICAL", "FIXED_PRICE");
        cheap.put("condition", "GOOD");
        cheap.put("price", 40);
        Map<String, Object> pricey = freeFormPayload(guides, uniqueTitle("Costly guide"), "PHYSICAL", "FIXED_PRICE");
        pricey.put("condition", "LIKE_NEW");
        pricey.put("price", 400);
        Map<String, Object> freebie = freeFormPayload(notes, uniqueTitle("Free notes"), "PHYSICAL", "FREE");
        freebie.put("condition", "GOOD");

        for (Map<String, Object> payload : List.of(cheap, pricey, freebie)) {
            assertThat(api.post("/api/v1/listings", payload, seller.token()).status())
                    .as("create %s: %s", payload.get("title"), payload).isEqualTo(201);
        }

        var byCategory = api.get("/api/v1/discovery/listings?categoryId=" + notes + "&size=12", null);
        assertThat(byCategory.status()).as("category filter: %s", byCategory.body()).isEqualTo(200);
        assertThat(byCategory.data().path("totalElements").asInt()).isEqualTo(2);
        byCategory.data().path("content").forEach(node ->
                assertThat(node.path("category").path("id").asString()).isEqualTo(notes.toString()));

        var priceRange = api.get("/api/v1/discovery/listings?minPrice=100&maxPrice=500", null);
        assertThat(priceRange.data().path("totalElements").asInt()).isEqualTo(1);
        assertThat(priceRange.data().path("content").get(0).path("title").asString())
                .isEqualTo(pricey.get("title"));

        var freeOnly = api.get("/api/v1/discovery/listings?priceType=FREE", null);
        assertThat(freeOnly.data().path("totalElements").asInt()).isEqualTo(1);

        var ascending = api.get("/api/v1/discovery/listings?sort=PRICE_ASC&size=12", null);
        assertThat(ascending.status()).as("sort: %s", ascending.body()).isEqualTo(200);
        var rows = ascending.data().path("content");
        assertThat(rows.get(0).path("priceType").asString()).isEqualTo("FREE");
        assertThat(rows.get(1).path("price").asDouble()).isEqualTo(40.0);
        assertThat(rows.get(2).path("price").asDouble()).isEqualTo(400.0);

        var descending = api.get("/api/v1/discovery/listings?sort=PRICE_DESC&size=12", null);
        assertThat(descending.data().path("content").get(0).path("price").asDouble()).isEqualTo(400.0);

        var byText = api.get("/api/v1/discovery/listings?q="
                + java.net.URLEncoder.encode("Costly", StandardCharsets.UTF_8), null);
        assertThat(byText.status()).isEqualTo(200);
        assertThat(byText.data().path("totalElements").asInt()).isGreaterThanOrEqualTo(1);
    }
}
