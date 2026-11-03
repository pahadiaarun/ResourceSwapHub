package com.bookbridge.communication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bookbridge.listing.SellerDto;

/**
 * One record for both conversation flavours: listing conversations fill the
 * listing* fields, request conversations fill the request* fields.
 */
public record ConversationSummaryDto(
        UUID id,
        UUID listingId,
        String listingTitle,
        String listingAuthor,
        String listingCoverUrl,
        String listingPriceType,
        BigDecimal listingPrice,
        String listingStatus,
        UUID requestId,
        String requestTitle,
        String requestStatus,
        SellerDto otherParticipant,
        MessageDto lastMessage,
        long unreadCount,
        Instant createdAt,
        Instant lastMessageAt
) {
}
