package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

/**
 * Integration coverage for the normalized education/employment records and the
 * server-side privacy rules (brief Parts 8-10). Exercised over real HTTP so the
 * security filter chain and visibility filtering are validated end to end.
 */
class ProfileRecordsIntegrationTest extends ApiIntegrationTest {

    private Map<String, Object> educationBody(String degree, String visibility) {
        Map<String, Object> body = new HashMap<>();
        body.put("institutionNameManual", "Test Institute of Technology");
        body.put("educationLevel", "UNDERGRADUATE");
        body.put("degree", degree);
        body.put("startYear", 2021);
        body.put("endYear", 2025);
        body.put("educationStatus", "CURRENT");
        body.put("current", true);
        body.put("visibility", visibility);
        return body;
    }

    @Test
    void educationCrudAndOwnership() {
        Session owner = register("Owner");
        Session other = register("Other");

        // Create
        var create = api.post("/api/v1/profile/me/education", educationBody("B.Sc", "PRIVATE"), owner.token());
        assertThat(create.status()).as("create status: %s", create.body()).isEqualTo(201);
        UUID id = UUID.fromString(create.data().path("id").asString());
        assertThat(create.data().path("degree").asString()).isEqualTo("B.Sc");
        assertThat(create.data().path("visibility").asString()).isEqualTo("PRIVATE");

        // List (owner sees own record)
        var list = api.get("/api/v1/profile/me/education", owner.token());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.data().isArray()).isTrue();
        assertThat(list.data().size()).isEqualTo(1);

        // Patch (partial update keeps other fields)
        var patch = api.patch("/api/v1/profile/me/education/" + id, Map.of("degree", "B.Tech"), owner.token());
        assertThat(patch.status()).as("patch status: %s", patch.body()).isEqualTo(200);
        assertThat(patch.data().path("degree").asString()).isEqualTo("B.Tech");
        assertThat(patch.data().path("educationLevel").asString()).isEqualTo("UNDERGRADUATE");

        // Ownership: another user cannot modify or delete the owner's record via /me
        var foreignPatch = api.patch("/api/v1/profile/me/education/" + id, Map.of("degree", "Hacked"), other.token());
        assertThat(foreignPatch.status()).as("foreign patch must be 404").isEqualTo(404);
        var foreignDelete = api.delete("/api/v1/profile/me/education/" + id, other.token());
        assertThat(foreignDelete.status()).as("foreign delete must be 404").isEqualTo(404);

        // Owner delete
        var delete = api.delete("/api/v1/profile/me/education/" + id, owner.token());
        assertThat(delete.status()).as("delete status").isEqualTo(204);
        var afterDelete = api.get("/api/v1/profile/me/education", owner.token());
        assertThat(afterDelete.data().size()).isZero();
    }

    @Test
    void educationVisibilityIsEnforcedServerSide() {
        Session owner = register("Owner");
        Session other = register("Other");

        api.post("/api/v1/profile/me/education", educationBody("Public Degree", "PUBLIC"), owner.token());
        api.post("/api/v1/profile/me/education", educationBody("Private Degree", "PRIVATE"), owner.token());

        // Owner's profileVisibility defaults to PUBLIC, so another user sees only PUBLIC records.
        var publicView = api.get("/api/v1/profile/" + owner.id() + "/education", other.token());
        assertThat(publicView.status()).isEqualTo(200);
        assertThat(publicView.data().size()).as("other user sees only public education").isEqualTo(1);
        assertThat(publicView.data().get(0).path("degree").asString()).isEqualTo("Public Degree");

        // Owner still sees both.
        var ownerView = api.get("/api/v1/profile/me/education", owner.token());
        assertThat(ownerView.data().size()).isEqualTo(2);

        // When the owner makes the whole profile PRIVATE, other users see nothing.
        var makePrivate = api.put("/api/v1/profile/me", Map.of("profileVisibility", "PRIVATE"), owner.token());
        assertThat(makePrivate.status()).as("profile visibility update: %s", makePrivate.body()).isEqualTo(200);
        var hiddenView = api.get("/api/v1/profile/" + owner.id() + "/education", other.token());
        assertThat(hiddenView.data().size()).as("private profile hides all education").isZero();
    }

    @Test
    void employmentRequiresDesignationAndFiltersByVisibility() {
        Session owner = register("Owner");
        Session other = register("Other");

        // Missing designation is rejected.
        Map<String, Object> noTitle = new HashMap<>();
        noTitle.put("companyName", "Acme");
        noTitle.put("visibility", "PUBLIC");
        var bad = api.post("/api/v1/profile/me/employment", noTitle, owner.token());
        assertThat(bad.status()).as("designation required: %s", bad.body()).isEqualTo(400);

        Map<String, Object> job = new HashMap<>();
        job.put("designation", "Engineer");
        job.put("companyName", "Acme");
        job.put("employmentType", "FULL_TIME");
        job.put("current", true);
        job.put("visibility", "PUBLIC");
        var created = api.post("/api/v1/profile/me/employment", job, owner.token());
        assertThat(created.status()).as("employment create: %s", created.body()).isEqualTo(201);

        Map<String, Object> privateJob = new HashMap<>();
        privateJob.put("designation", "Consultant");
        privateJob.put("visibility", "PRIVATE");
        api.post("/api/v1/profile/me/employment", privateJob, owner.token());

        var otherView = api.get("/api/v1/profile/" + owner.id() + "/employment", other.token());
        assertThat(otherView.data().size()).as("only public employment exposed").isEqualTo(1);
        assertThat(otherView.data().get(0).path("designation").asString()).isEqualTo("Engineer");
    }

    @Test
    void otherUsersOnlyReceiveThePublicIdentityCard() {
        Session owner = register("Owner");
        Session other = register("Other");

        Map<String, Object> update = new HashMap<>();
        update.put("bio", "A private bio");
        update.put("state", "Delhi");
        update.put("profileVisibility", "PUBLIC");
        var saved = api.put("/api/v1/profile/me", update, owner.token());
        assertThat(saved.status()).isEqualTo(200);

        // Owner sees their full profile.
        JsonNode mine = api.get("/api/v1/profile/me", owner.token()).data();
        assertThat(mine.path("bio").asString()).isEqualTo("A private bio");
        assertThat(mine.path("email").asString()).contains("@");

        // Another user only ever gets the minimal public card. Withheld fields are omitted by the
        // serializer's non_null setting, so presence itself is the thing under test.
        JsonNode theirs = api.get("/api/v1/profile/" + owner.id(), other.token()).data();
        assertThat(theirs.path("id").asString()).isEqualTo(owner.id().toString());
        assertThat(theirs.path("name").asString()).isNotBlank();
        assertThat(theirs.has("email")).as("email never exposed").isFalse();
        assertThat(theirs.has("bio")).as("bio never exposed").isFalse();
        assertThat(theirs.has("state")).as("state never exposed").isFalse();
        assertThat(theirs.has("location")).as("location never exposed").isFalse();
    }

    @Test
    void profileEndpointsRequireAuthentication() {
        var anon = api.get("/api/v1/profile/me/education", null);
        assertThat(anon.status()).as("anonymous education access").isIn(401, 403);
    }
}
