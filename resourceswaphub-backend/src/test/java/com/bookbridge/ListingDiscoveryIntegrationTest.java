package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ListingDiscoveryIntegrationTest extends ApiIntegrationTest {

    @Test
    void createsCollectionAndListingThenDiscoversWithFilters() {
        Session owner = registerWithProfile("Aisha Kapoor", "Pune", "Kothrud");
        UUID bookId = createBook(uniqueTitle("NCERT Mathematics"), "Mathematics", 10, "CBSE");

        var collection = api.post("/api/v1/collections", Map.of(
                "title", "Class 10 essentials",
                "description", "Everything for board exam revision.",
                "city", "Pune",
                "locality", "Kothrud"
        ), owner.token());
        assertThat(collection.status()).isEqualTo(201);
        String collectionId = collection.data().path("id").asString();

        var create = api.post("/api/v1/listings", Map.of(
                "bookId", bookId.toString(),
                "collectionId", collectionId,
                "condition", "LIKE_NEW",
                "priceType", "FREE",
                "city", "Pune",
                "locality", "Kothrud",
                "notes", "Happy to pass this on.",
                "imageIds", java.util.List.of()
        ), owner.token());
        assertThat(create.status()).as("listing create: %s", create.body()).isEqualTo(201);
        String listingId = create.data().path("id").asString();
        assertThat(create.data().path("price").isNull() || create.data().path("price").isMissingNode()).isTrue();
        assertThat(create.data().path("status").asString()).isEqualTo("AVAILABLE");
        assertThat(create.data().path("collection").path("id").asString()).isEqualTo(collectionId);

        var collectionDetail = api.get("/api/v1/collections/" + collectionId, owner.token());
        assertThat(collectionDetail.status()).isEqualTo(200);
        assertThat(collectionDetail.data().path("items").size()).isEqualTo(1);

        var search = api.get("/api/v1/discovery/listings?q=Mathematics&classLevel=10&board=CBSE"
                + "&priceType=FREE&condition=LIKE_NEW&city=Pune&page=0&size=12", null);
        assertThat(search.status()).as("discovery: %s", search.body()).isEqualTo(200);
        assertThat(search.data().path("content").size()).isEqualTo(1);
        assertThat(search.data().path("content").get(0).path("id").asString()).isEqualTo(listingId);
        assertThat(search.data().path("totalElements").asLong()).isEqualTo(1);

        var miss = api.get("/api/v1/discovery/listings?classLevel=12", null);
        assertThat(miss.data().path("content").size()).isZero();

        var detail = api.get("/api/v1/listings/" + listingId, null);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.data().path("book").path("title").asString()).contains("NCERT Mathematics");

        var profileRequired = api.post("/api/v1/listings",
                listingPayload(bookId, "FREE", "Pune"), register("No Profile").token());
        assertThat(profileRequired.status()).isEqualTo(400);
        assertThat(profileRequired.body().path("error").path("code").asString()).isEqualTo("PROFILE_INCOMPLETE");
    }

    @Test
    void enforcesOwnershipAndStatusLifecycle() {
        Session owner = registerWithProfile("Meera Iyer", "Bengaluru", "Jayanagar");
        Session stranger = registerWithProfile("Rohan Mehta", "Pune", "Baner");
        UUID bookId = createBook(uniqueTitle("ICSE Physics"), "Physics", 9, "ICSE");
        UUID listingId = createListing(owner, bookId);

        var strangerUpdate = api.patch("/api/v1/listings/" + listingId, Map.of(
                "condition", "FAIR",
                "priceType", "FREE",
                "city", "Pune",
                "notes", "Changed by someone else"
        ), stranger.token());
        assertThat(strangerUpdate.status()).isEqualTo(404);

        var strangerStatus = api.patch("/api/v1/listings/" + listingId + "/status",
                Map.of("status", "WITHDRAWN"), stranger.token());
        assertThat(strangerStatus.status()).isEqualTo(404);

        var unavailable = api.patch("/api/v1/listings/" + listingId + "/status",
                Map.of("status", "UNAVAILABLE"), owner.token());
        assertThat(unavailable.status()).isEqualTo(200);
        assertThat(unavailable.data().path("status").asString()).isEqualTo("UNAVAILABLE");

        var hidden = api.get("/api/v1/discovery/listings?city=Bengaluru", null);
        assertThat(hidden.data().path("content").size()).isZero();

        var availableAgain = api.patch("/api/v1/listings/" + listingId + "/status",
                Map.of("status", "AVAILABLE"), owner.token());
        assertThat(availableAgain.data().path("status").asString()).isEqualTo("AVAILABLE");

        var withdrawn = api.patch("/api/v1/listings/" + listingId + "/status",
                Map.of("status", "WITHDRAWN"), owner.token());
        assertThat(withdrawn.status()).isEqualTo(200);

        var hiddenFromStranger = api.get("/api/v1/listings/" + listingId, stranger.token());
        assertThat(hiddenFromStranger.status()).isEqualTo(404);
        var visibleToOwner = api.get("/api/v1/listings/" + listingId, owner.token());
        assertThat(visibleToOwner.status()).isEqualTo(200);
    }

    @Test
    void validatesPriceRulesAndRequiresListingOwnerForCollections() {
        Session owner = registerWithProfile("Aisha Kapoor", "Pune", "Kothrud");
        Session stranger = registerWithProfile("Rohan Mehta", "Pune", "Baner");
        UUID bookId = createBook(uniqueTitle("NCERT Science"), "Science", 10, "CBSE");

        var freeWithPrice = api.post("/api/v1/listings", Map.of(
                "bookId", bookId.toString(),
                "condition", "GOOD",
                "priceType", "FREE",
                "price", 100,
                "city", "Pune",
                "locality", "Kothrud",
                "imageIds", java.util.List.of()
        ), owner.token());
        assertThat(freeWithPrice.status()).isEqualTo(400);
        assertThat(freeWithPrice.body().path("error").path("code").asString()).isEqualTo("INVALID_PRICE");

        var fixedWithoutPrice = api.post("/api/v1/listings", Map.of(
                "bookId", bookId.toString(),
                "condition", "GOOD",
                "priceType", "FIXED_PRICE",
                "city", "Pune",
                "locality", "Kothrud",
                "imageIds", java.util.List.of()
        ), owner.token());
        assertThat(fixedWithoutPrice.status()).isEqualTo(400);
        assertThat(fixedWithoutPrice.body().path("error").path("code").asString()).isEqualTo("INVALID_PRICE");

        var collection = api.post("/api/v1/collections", Map.of(
                "title", "Pune Class 10 set",
                "city", "Pune",
                "locality", "Kothrud"
        ), owner.token());
        String collectionId = collection.data().path("id").asString();

        var foreignCollection = api.post("/api/v1/listings", Map.of(
                "bookId", bookId.toString(),
                "collectionId", collectionId,
                "condition", "GOOD",
                "priceType", "FREE",
                "city", "Pune",
                "locality", "Baner",
                "imageIds", java.util.List.of()
        ), stranger.token());
        assertThat(foreignCollection.status()).isEqualTo(403);
    }

    @Test
    void paginatesMyListingsAndCatalogueSearchReusesCanonicalBooks() {
        Session owner = registerWithProfile("Meera Iyer", "Bengaluru", "Jayanagar");
        UUID bookId = createBook(uniqueTitle("ICSE Mathematics"), "Mathematics", 9, "ICSE");
        createListing(owner, bookId);
        createListing(owner, bookId);

        var mine = api.get("/api/v1/users/me/listings?page=0&size=1", owner.token());
        assertThat(mine.status()).isEqualTo(200);
        assertThat(mine.data().path("content").size()).isEqualTo(1);
        assertThat(mine.data().path("totalElements").asLong()).isEqualTo(2);
        assertThat(mine.data().path("totalPages").asInt()).isEqualTo(2);

        var catalogue = api.get("/api/v1/catalog/books?q=ICSE%20Mathematics", owner.token());
        assertThat(catalogue.status()).isEqualTo(200);
        assertThat(catalogue.data().size()).isEqualTo(1);

        var second = api.post("/api/v1/catalog/books", Map.of(
                "title", catalogue.data().get(0).path("title").asString(),
                "author", catalogue.data().get(0).path("author").asString(),
                "publisher", "Test Press",
                "classLevel", 9,
                "board", "ICSE",
                "subject", "Mathematics",
                "edition", "2024",
                "language", "English"
        ), owner.token());
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.data().path("id").asString()).isEqualTo(bookId.toString());
    }
}
