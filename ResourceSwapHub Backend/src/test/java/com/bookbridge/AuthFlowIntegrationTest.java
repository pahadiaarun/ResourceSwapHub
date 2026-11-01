package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class AuthFlowIntegrationTest extends ApiIntegrationTest {

    @Test
    void registersSignsInRefreshesAndSignsOut() {
        var registered = api.post("/api/v1/auth/register", Map.of(
                "name", "Aisha Kapoor",
                "email", "aisha.flow@bookbridge.test",
                "password", "BookBridge#2026"
        ), null);

        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.data().path("accessToken").asString()).isNotBlank();
        assertThat(registered.data().path("user").path("email").asString())
                .isEqualTo("aisha.flow@bookbridge.test");
        assertThat(registered.data().path("user").path("profileComplete").asBoolean()).isFalse();
        String refresh = api.cookie(registered, "bb_refresh");
        assertThat(refresh).isNotBlank();

        var duplicate = api.post("/api/v1/auth/register", Map.of(
                "name", "Someone Else",
                "email", "AISHA.FLOW@bookbridge.test",
                "password", "BookBridge#2026"
        ), null);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().path("error").path("code").asString()).isEqualTo("EMAIL_IN_USE");

        var badLogin = api.post("/api/v1/auth/login", Map.of(
                "email", "aisha.flow@bookbridge.test",
                "password", "wrong-password"
        ), null);
        assertThat(badLogin.status()).isEqualTo(401);

        var login = api.post("/api/v1/auth/login", Map.of(
                "email", "aisha.flow@bookbridge.test",
                "password", "BookBridge#2026"
        ), null);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.data().path("user").path("name").asString()).isEqualTo("Aisha Kapoor");
        String loginRefresh = api.cookie(login, "bb_refresh");

        var me = api.get("/api/v1/users/me", login.data().path("accessToken").asString());
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.data().path("email").asString()).isEqualTo("aisha.flow@bookbridge.test");

        var unauthorized = api.get("/api/v1/users/me", null);
        assertThat(unauthorized.status()).isEqualTo(401);

        var refreshed = api.postWithCookie("/api/v1/auth/refresh", null, "bb_refresh=" + loginRefresh);
        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.data().path("accessToken").asString()).isNotBlank();
        String rotatedRefresh = api.cookie(refreshed, "bb_refresh");
        assertThat(rotatedRefresh).isNotEqualTo(loginRefresh);

        var replay = api.postWithCookie("/api/v1/auth/refresh", null, "bb_refresh=" + loginRefresh);
        assertThat(replay.status()).isEqualTo(401);
        assertThat(replay.body().path("error").path("code").asString()).isEqualTo("UNAUTHORIZED");

        var logout = api.postWithCookie("/api/v1/auth/logout", null, "bb_refresh=" + refresh);
        assertThat(logout.status()).isEqualTo(204);

        var afterLogout = api.postWithCookie("/api/v1/auth/refresh", null, "bb_refresh=" + refresh);
        assertThat(afterLogout.status()).isEqualTo(401);
    }

    @Test
    void completesProfileAndRejectsInvalidInput() {
        Session session = register("Rohan Mehta");

        var invalid = api.patch("/api/v1/users/me", Map.of("name", "R"), session.token());
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().path("error").path("code").asString()).isEqualTo("VALIDATION_ERROR");

        var completed = api.patch("/api/v1/users/me", Map.of(
                "name", "Rohan Mehta",
                "city", "Pune",
                "locality", "Baner"
        ), session.token());
        assertThat(completed.status()).isEqualTo(200);
        assertThat(completed.data().path("city").asString()).isEqualTo("Pune");
        assertThat(completed.data().path("locality").asString()).isEqualTo("Baner");
        assertThat(completed.data().path("profileComplete").asBoolean()).isTrue();

        var freshRead = api.get("/api/v1/users/me", session.token());
        assertThat(freshRead.data().path("profileComplete").asBoolean()).isTrue();
        assertThat(freshRead.data().path("city").asString()).isEqualTo("Pune");
    }

    @Test
    void rejectsWeakRegistrationPayload() {
        var response = api.post("/api/v1/auth/register", Map.of(
                "name", "A",
                "email", "not-an-email",
                "password", "short"
        ), null);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("error").path("details").size()).isGreaterThanOrEqualTo(2);
    }
}
