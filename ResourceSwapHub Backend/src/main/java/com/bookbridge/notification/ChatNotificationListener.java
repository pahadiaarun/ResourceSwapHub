package com.bookbridge.notification;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bookbridge.communication.ChatEventPayload;
import com.bookbridge.communication.ConversationParticipantRepository;
import com.bookbridge.communication.MessageCreatedEvent;

@Component
public class ChatNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(ChatNotificationListener.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ConversationParticipantRepository participantRepository;

    public ChatNotificationListener(SimpMessagingTemplate messagingTemplate,
                                    ConversationParticipantRepository participantRepository) {
        this.messagingTemplate = messagingTemplate;
        this.participantRepository = participantRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageCreatedEvent event) {
        for (UUID participantId : event.participantIds()) {
            long unread = participantRepository.unreadInConversation(event.conversationId(), participantId);
            ChatEventPayload payload = new ChatEventPayload(
                    "MESSAGE",
                    event.conversationId(),
                    event.message(),
                    unread
            );
            try {
                messagingTemplate.convertAndSendToUser(
                        participantId.toString(),
                        "/queue/chat-events",
                        payload
                );
            } catch (RuntimeException ex) {
                log.warn("Could not deliver chat event to user {}: {}", participantId, ex.getMessage());
            }
        }
    }
}
