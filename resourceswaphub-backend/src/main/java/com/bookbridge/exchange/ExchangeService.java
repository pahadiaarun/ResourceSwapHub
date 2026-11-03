package com.bookbridge.exchange;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.communication.Conversation;
import com.bookbridge.communication.ConversationRepository;
import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.ListingService;
import com.bookbridge.listing.ListingStatus;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.security.SecurityUtils;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class ExchangeService {

    private final ExchangeRepository exchangeRepository;
    private final ListingRepository listingRepository;
    private final ListingService listingService;
    private final UserService userService;
    private final ConversationRepository conversationRepository;
    private final com.bookbridge.communication.ConversationParticipantRepository participantRepository;

    public ExchangeService(ExchangeRepository exchangeRepository, ListingRepository listingRepository,
                           ListingService listingService, UserService userService,
                           ConversationRepository conversationRepository,
                           com.bookbridge.communication.ConversationParticipantRepository participantRepository) {
        this.exchangeRepository = exchangeRepository;
        this.listingRepository = listingRepository;
        this.listingService = listingService;
        this.userService = userService;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
    }

    /**
     * Explicit confirmation. Idempotent per person: confirming twice does
     * nothing, and the exchange only completes when both sides have confirmed.
     */
    @Transactional
    public ExchangeDto confirm(UUID userId, UUID listingId, UUID conversationId) {
        User actor = userService.require(userId);
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (listing.getStatus() == ListingStatus.EXCHANGED) {
            throw ApiException.badRequest("ALREADY_EXCHANGED", "This resource is already exchanged.");
        }
        if (listing.getStatus() == ListingStatus.WITHDRAWN) {
            throw ApiException.badRequest("LISTING_WITHDRAWN", "Withdrawn listings cannot be exchanged.");
        }

        boolean actorIsOwner = listing.getOwner().getId().equals(userId);
        Exchange exchange = exchangeRepository.findByListingIdAndStatusNot(listingId, ExchangeStatus.CANCELLED)
                .orElse(null);

        if (exchange == null) {
            exchange = new Exchange();
            exchange.setListing(listing);
            exchange.setOwner(listing.getOwner());
            if (conversationId != null) {
                exchange.setConversation(linkedConversation(conversationId, listing, actor));
            }
            if (actorIsOwner) {
                exchange.setOwnerConfirmedAt(Instant.now());
            } else {
                exchange.setParticipant(actor);
                exchange.setParticipantConfirmedAt(Instant.now());
            }
            exchange.setStatus(ExchangeStatus.PENDING);
        } else {
            validateParticipants(exchange, userId);
            if (conversationId != null && exchange.getConversation() == null) {
                exchange.setConversation(linkedConversation(conversationId, listing, actor));
            }
            if (actorIsOwner) {
                assertNotReconfirmed(exchange.getOwnerConfirmedAt() == null, "already confirmed");
                exchange.setOwnerConfirmedAt(Instant.now());
            } else {
                if (exchange.getParticipant() != null && !exchange.getParticipant().getId().equals(userId)) {
                    throw ApiException.conflict("EXCHANGE_PARTICIPANT_TAKEN",
                            "Another user already confirmed this exchange.");
                }
                assertNotReconfirmed(exchange.getParticipantConfirmedAt() == null, "already confirmed");
                exchange.setParticipant(actor);
                exchange.setParticipantConfirmedAt(Instant.now());
            }
        }

        boolean both = exchange.getOwnerConfirmedAt() != null
                && exchange.getParticipantConfirmedAt() != null;
        exchange.setStatus(both ? ExchangeStatus.CONFIRMED : ExchangeStatus.PENDING);
        if (both) {
            exchange.setConfirmedAt(Instant.now());
        }
        exchangeRepository.saveAndFlush(exchange);

        if (both) {
            listingService.transition(listing, ListingStatus.EXCHANGED, actor, "EXCHANGE");
            listingRepository.save(listing);
        }
        return toDto(exchange, userId);
    }

    @Transactional
    public void cancel(UUID userId, UUID listingId) {
        Exchange exchange = exchangeRepository.findByListingIdAndStatusNot(listingId, ExchangeStatus.CANCELLED)
                .orElseThrow(() -> ApiException.notFound("No exchange is pending for this listing."));
        boolean owner = exchange.getOwner().getId().equals(userId);
        boolean participant = exchange.getParticipant() != null
                && exchange.getParticipant().getId().equals(userId);
        if (!owner && !participant) {
            throw ApiException.notFound("No exchange is pending for this listing.");
        }
        if (exchange.getStatus() == ExchangeStatus.CONFIRMED) {
            throw ApiException.badRequest("EXCHANGE_CONFIRMED",
                    "A confirmed exchange cannot be cancelled.");
        }
        exchange.setStatus(ExchangeStatus.CANCELLED);
        exchangeRepository.saveAndFlush(exchange);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExchangeDto> mine(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<Exchange> result = exchangeRepository.forUser(userId, pageable);
        Page<ExchangeDto> mapped = result.map(exchange -> toDto(exchange, userId));
        return new PageResponse<>(mapped.getContent(), Math.max(0, page), Math.max(1, Math.min(size, 48)),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional(readOnly = true)
    public ExchangeDto forListing(UUID userId, UUID listingId) {
        Exchange exchange = exchangeRepository.findByListingIdAndStatusNot(listingId, ExchangeStatus.CANCELLED)
                .orElseThrow(() -> ApiException.notFound("No exchange is in progress for this listing."));
        validateParticipants(exchange, userId);
        return toDto(exchange, userId);
    }

    // ------------------------------------------------------------------------

    private Conversation linkedConversation(UUID conversationId, Listing listing, User actor) {
        Conversation conversation = conversationRepository.findDetailedById(conversationId)
                .orElseThrow(() -> ApiException.badRequest("CONVERSATION_NOT_FOUND",
                        "That conversation could not be found."));
        boolean member = participantRepository.findByConversationId(conversationId).stream()
                .anyMatch(participant -> participant.getUser().getId().equals(actor.getId()));
        if (!member) {
            throw ApiException.forbidden("You can only link conversations you take part in.");
        }
        if (conversation.getListing() == null
                || !conversation.getListing().getId().equals(listing.getId())) {
            throw ApiException.badRequest("CONVERSATION_MISMATCH",
                    "That conversation does not belong to this listing.");
        }
        return conversation;
    }

    private void validateParticipants(Exchange exchange, UUID userId) {
        boolean owner = exchange.getOwner().getId().equals(userId);
        boolean linkedParticipant = exchange.getParticipant() != null
                && exchange.getParticipant().getId().equals(userId);
        if (!owner && !linkedParticipant) {
            // A third party may never see or touch an exchange between two other users.
            throw ApiException.notFound("That exchange could not be found.");
        }
    }

    private void assertNotReconfirmed(boolean allowed, String message) {
        if (!allowed) {
            throw ApiException.conflict("ALREADY_CONFIRMED",
                    "You have " + message + " this exchange.");
        }
    }

    private ExchangeDto toDto(Exchange exchange, UUID viewerId) {
        Listing listing = exchange.getListing();
        String title = listing.getBook() != null
                ? listing.getBook().getTitle()
                : listing.getTitle();
        boolean viewerOwner = exchange.getOwner().getId().equals(viewerId);
        boolean viewerConfirmed = viewerOwner
                ? exchange.getOwnerConfirmedAt() != null
                : exchange.getParticipantConfirmedAt() != null;
        return new ExchangeDto(
                exchange.getId(),
                listing.getId(),
                title,
                listing.getStatus().name(),
                exchange.getOwner().getId(),
                com.bookbridge.listing.SellerDto.from(exchange.getOwner()),
                exchange.getParticipant() == null ? null : com.bookbridge.listing.SellerDto.from(exchange.getParticipant()),
                viewerOwner,
                exchange.getStatus().name(),
                exchange.getConfirmedAt(),
                exchange.getCreatedAt(),
                viewerConfirmed
        );
    }
}
