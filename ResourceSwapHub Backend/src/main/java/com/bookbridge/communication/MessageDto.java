package com.bookbridge.communication;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.listing.SellerDto;

public record MessageDto(
        long id,
        UUID conversationId,
        SellerDto sender,
        String body,
        Instant createdAt
) {
    public static MessageDto from(Message message) {
        return new MessageDto(
                message.getId(),
                message.getConversation().getId(),
                SellerDto.from(message.getSender()),
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
