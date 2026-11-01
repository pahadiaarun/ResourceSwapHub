package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the application on a random port against the bookbridge_test database.
 * Each test starts from an empty schema; Flyway owns the schema itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiIntegrationTest {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected ApiClient api;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE TABLE users, books, media_assets, conversations RESTART IDENTITY CASCADE");
        api = new ApiClient(port);
    }

    protected record Session(UUID id, String email, String token, String refreshCookie) {
    }

    protected Session register(String name) {
        String email = "reader" + COUNTER.incrementAndGet() + "-" + UUID.randomUUID() + "@bookbridge.test";
        var response = api.post("/api/v1/auth/register", Map.of(
                "name", name,
                "email", email,
                "password", "BookBridge#2026"
        ), null);
        assertThat(response.status()).as("register status").isEqualTo(201);
        return new Session(
                UUID.fromString(response.data().path("user").path("id").asString()),
                email,
                response.data().path("accessToken").asString(),
                api.cookie(response, "bb_refresh")
        );
    }

    protected Session registerWithProfile(String name, String city, String locality) {
        Session session = register(name);
        var response = api.patch("/api/v1/users/me", Map.of(
                "name", name,
                "city", city,
                "locality", locality
        ), session.token());
        assertThat(response.status()).as("profile update status").isEqualTo(200);
        assertThat(response.data().path("profileComplete").asBoolean()).isTrue();
        return session;
    }

    protected UUID createBook(String title, String subject, int classLevel, String board) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("author", "BookBridge Test Author");
        payload.put("publisher", "Test Press");
        payload.put("classLevel", classLevel);
        payload.put("board", board);
        payload.put("subject", subject);
        payload.put("edition", "2024");
        payload.put("language", "English");
        var response = api.post("/api/v1/catalog/books", payload, register("Catalogue Contributor").token());
        assertThat(response.status()).as("book create status: %s", response.body()).isEqualTo(200);
        return UUID.fromString(response.data().path("id").asString());
    }

    protected Map<String, Object> listingPayload(UUID bookId, String priceType, String city) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bookId", bookId.toString());
        payload.put("condition", "GOOD");
        payload.put("priceType", priceType);
        if ("FIXED_PRICE".equals(priceType)) {
            payload.put("price", 250);
        } else if ("NEGOTIABLE".equals(priceType)) {
            payload.put("price", 180);
        } else {
            payload.put("price", null);
        }
        payload.put("city", city);
        payload.put("locality", "Kothrud");
        payload.put("notes", "Clean pages, minor shelf wear.");
        payload.put("imageIds", List.of());
        return payload;
    }

    protected UUID createListing(Session owner, UUID bookId) {
        var response = api.post("/api/v1/listings", listingPayload(bookId, "FIXED_PRICE", "Pune"), owner.token());
        assertThat(response.status()).as("listing create status: %s", response.body()).isEqualTo(201);
        return UUID.fromString(response.data().path("id").asString());
    }

    protected String uniqueTitle(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }
}
