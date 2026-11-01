package com.bookbridge.communication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bookbridge.listing.SellerDto;

public record ConversationSummaryDto(
        UUID id,
        UUID listingId,
        String listingTitle,
        String listingAuthor,
        String listingCoverUrl,
        String listingPriceType,
        BigDecimal listingPrice,
        String listingStatus,
        SellerDto otherParticipant,
        MessageDto lastMessage,
        long unreadCount,
        Instant createdAt,
        Instant lastMessageAt
) {
}
