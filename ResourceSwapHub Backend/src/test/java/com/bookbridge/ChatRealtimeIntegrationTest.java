package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.bookbridge.communication.ChatEventPayload;

class ChatRealtimeIntegrationTest extends ApiIntegrationTest {

    @Test
    void deliversNewMessagesToTheOtherParticipantOverWebSocket() throws Exception {
        Session owner = registerWithProfile("Aisha Kapoor", "Pune", "Kothrud");
        Session buyer = registerWithProfile("Rohan Mehta", "Pune", "Baner");
        UUID bookId = createBook(uniqueTitle("NCERT Mathematics"), "Mathematics", 10, "CBSE");
        UUID listingId = createListing(owner, bookId);

        String conversationId = api.post("/api/v1/conversations",
                Map.of("listingId", listingId.toString()), buyer.token())
                .data().path("id").asString();

        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + owner.token());

        StompSession session = stomp.connectAsync(
                        "ws://127.0.0.1:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);

        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<ChatEventPayload> payload = new AtomicReference<>();
        session.subscribe("/user/queue/chat-events", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatEventPayload.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object body) {
                payload.set((ChatEventPayload) body);
                received.countDown();
            }
        });
        Thread.sleep(500);

        var sent = api.post("/api/v1/conversations/" + conversationId + "/messages",
                Map.of("body", "Live delivery check"), buyer.token());
        assertThat(sent.status()).isEqualTo(201);

        assertThat(received.await(10, TimeUnit.SECONDS))
                .as("WebSocket event should reach the owner")
                .isTrue();
        assertThat(payload.get().type()).isEqualTo("MESSAGE");
        assertThat(payload.get().conversationId().toString()).isEqualTo(conversationId);
        assertThat(payload.get().message().body()).isEqualTo("Live delivery check");
        assertThat(payload.get().unreadCount()).isEqualTo(1);

        session.disconnect();
        stomp.stop();
    }
}
