package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChatFlowIntegrationTest extends ApiIntegrationTest {

    @Test
    void startsConversationSendsMessagesAndTracksUnread() {
        Session owner = registerWithProfile("Aisha Kapoor", "Pune", "Kothrud");
        Session buyer = registerWithProfile("Rohan Mehta", "Pune", "Baner");
        UUID bookId = createBook(uniqueTitle("NCERT Mathematics"), "Mathematics", 10, "CBSE");
        UUID listingId = createListing(owner, bookId);

        var started = api.post("/api/v1/conversations", Map.of("listingId", listingId.toString()), buyer.token());
        assertThat(started.status()).as("start: %s", started.body()).isEqualTo(201);
        String conversationId = started.data().path("id").asString();
        assertThat(started.data().path("listingId").asString()).isEqualTo(listingId.toString());
        assertThat(started.data().path("otherParticipant").path("name").asString()).isEqualTo("Aisha Kapoor");
        assertThat(started.data().path("unreadCount").asLong()).isZero();

        var again = api.post("/api/v1/conversations", Map.of("listingId", listingId.toString()), buyer.token());
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.data().path("id").asString()).isEqualTo(conversationId);

        var ownListing = api.post("/api/v1/conversations", Map.of("listingId", listingId.toString()), owner.token());
        assertThat(ownListing.status()).isEqualTo(400);
        assertThat(ownListing.body().path("error").path("code").asString()).isEqualTo("OWN_LISTING");

        var sent = api.post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("body", "Hi Aisha, is this still available?"), buyer.token());
        assertThat(sent.status()).as("send: %s", sent.body()).isEqualTo(201);
        long messageId = sent.data().path("id").asLong();
        assertThat(sent.data().path("body").asString()).isEqualTo("Hi Aisha, is this still available?");

        var ownerInbox = api.get("/api/v1/conversations?page=0&size=20", owner.token());
        assertThat(ownerInbox.status()).isEqualTo(200);
        assertThat(ownerInbox.data().path("content").size()).isEqualTo(1);
        assertThat(ownerInbox.data().path("content").get(0).path("unreadCount").asLong()).isEqualTo(1);
        assertThat(ownerInbox.data().path("content").get(0).path("lastMessage").path("body").asString())
                .contains("still available");

        var unread = api.get("/api/v1/conversations/unread-count", owner.token());
        assertThat(unread.data().path("count").asLong()).isEqualTo(1);

        var messages = api.get("/api/v1/conversations/" + conversationId + "/messages", owner.token());
        assertThat(messages.status()).isEqualTo(200);
        assertThat(messages.data().path("messages").size()).isEqualTo(1);
        assertThat(messages.data().path("messages").get(0).path("id").asLong()).isEqualTo(messageId);

        var reply = api.post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("body", "Yes, it is. Would Saturday work?"), owner.token());
        assertThat(reply.status()).isEqualTo(201);

        var buyerUnread = api.get("/api/v1/conversations/unread-count", buyer.token());
        assertThat(buyerUnread.data().path("count").asLong()).isEqualTo(1);

        var read = api.post("/api/v1/conversations/" + conversationId + "/read",
                Map.of("lastMessageId", reply.data().path("id").asLong()), buyer.token());
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.data().path("count").asLong()).isZero();

        var afterRead = api.get("/api/v1/conversations/unread-count", buyer.token());
        assertThat(afterRead.data().path("count").asLong()).isZero();
    }

    @Test
    void blocksOutsidersFromEveryConversationResource() {
        Session owner = registerWithProfile("Aisha Kapoor", "Pune", "Kothrud");
        Session buyer = registerWithProfile("Rohan Mehta", "Pune", "Baner");
        Session outsider = registerWithProfile("Meera Iyer", "Bengaluru", "Jayanagar");
        UUID bookId = createBook(uniqueTitle("NCERT Biology"), "Biology", 12, "CBSE");
        UUID listingId = createListing(owner, bookId);

        String conversationId = api.post("/api/v1/conversations",
                Map.of("listingId", listingId.toString()), buyer.token())
                .data().path("id").asString();

        assertThat(api.get("/api/v1/conversations/" + conversationId, outsider.token()).status()).isEqualTo(404);
        assertThat(api.get("/api/v1/conversations/" + conversationId + "/messages", outsider.token()).status())
                .isEqualTo(404);
        assertThat(api.post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("body", "Let me in"), outsider.token()).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/conversations/" + conversationId + "/read",
                Map.of("lastMessageId", 1), outsider.token()).status()).isEqualTo(404);

        var outsiderInbox = api.get("/api/v1/conversations", outsider.token());
        assertThat(outsiderInbox.data().path("content").size()).isZero();
        assertThat(api.get("/api/v1/conversations/unread-count", outsider.token())
                .data().path("count").asLong()).isZero();
    }

    @Test
    void rejectsMessagingIncompleteProfilesAndUnavailableListings() {
        Session owner = registerWithProfile("Aisha Kapoor", "Pune", "Kothrud");
        Session incomplete = register("Incomplete Reader");
        UUID bookId = createBook(uniqueTitle("NCERT Chemistry"), "Chemistry", 12, "CBSE");
        UUID listingId = createListing(owner, bookId);

        var blocked = api.post("/api/v1/conversations",
                Map.of("listingId", listingId.toString()), incomplete.token());
        assertThat(blocked.status()).isEqualTo(400);
        assertThat(blocked.body().path("error").path("code").asString()).isEqualTo("PROFILE_INCOMPLETE");

        api.patch("/api/v1/listings/" + listingId + "/status", Map.of("status", "WITHDRAWN"), owner.token());
        Session buyer = registerWithProfile("Rohan Mehta", "Pune", "Baner");
        var unavailable = api.post("/api/v1/conversations",
                Map.of("listingId", listingId.toString()), buyer.token());
        assertThat(unavailable.status()).isEqualTo(400);
        assertThat(unavailable.body().path("error").path("code").asString()).isEqualTo("LISTING_UNAVAILABLE");
    }
}
