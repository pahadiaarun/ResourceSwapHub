package com.bookbridge.communication;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingService;
import com.bookbridge.listing.SellerDto;
import com.bookbridge.notification.NotificationService;
import com.bookbridge.notification.NotificationType;
import com.bookbridge.request.ResourceRequest;
import com.bookbridge.request.RequestStatus;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class ChatService {
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserService userService;
    private final ListingService listingService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public ChatService(ConversationRepository conversationRepository,
                       ConversationParticipantRepository participantRepository,
                       MessageRepository messageRepository,
                       UserService userService,
                       ListingService listingService,
                       NotificationService notificationService,
                       ApplicationEventPublisher eventPublisher) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.listingService = listingService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ConversationSummaryDto start(UUID userId, UUID listingId) {
        User viewer = userService.require(userId);
        if (!viewer.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before messaging a book owner.");
        }
        Listing listing = listingService.requireAvailableForConversation(listingId, userId);
        String directKey = directKey(listingId, listing.getOwner().getId(), userId);
        Conversation existing = conversationRepository.findByDirectKey(directKey).orElse(null);
        if (existing != null) {
            return summary(existing, userId);
        }
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation.setListing(listing);
        conversation.setDirectKey(directKey);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.saveAndFlush(conversation);

        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(conversation);
        owner.setUser(listing.getOwner());
        owner.setRole(ParticipantRole.OWNER);
        ConversationParticipant interested = new ConversationParticipant();
        interested.setConversation(conversation);
        interested.setUser(viewer);
        interested.setRole(ParticipantRole.INTERESTED);
        participantRepository.saveAll(List.of(owner, interested));
        return summary(conversation, userId);
    }

    /**
     * Reuses the direct-chat model for resource requests: one conversation per
     * (request, user pair), never a second messaging system. The requester
     * receives a REQUEST_RESPONSE notification the first time someone responds,
     * but only while the request is still open.
     */
    @Transactional
    public ConversationSummaryDto startForRequest(UUID userId, ResourceRequest request) {
        User responder = userService.require(userId);
        if (!responder.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before responding to a request.");
        }
        UUID requesterId = request.getRequester().getId();
        String directKey = "req:" + request.getId() + ":" + directPair(requesterId, userId);
        Conversation existing = conversationRepository.findByDirectKey(directKey).orElse(null);
        if (existing != null) {
            return summary(existing, userId);
        }
        Conversation conversation = new Conversation();
        conversation.setRequest(request);
        conversation.setDirectKey(directKey);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.saveAndFlush(conversation);

        ConversationParticipant owner = new ConversationParticipant();
        owner.setConversation(conversation);
        owner.setUser(request.getRequester());
        owner.setRole(ParticipantRole.OWNER);
        ConversationParticipant interested = new ConversationParticipant();
        interested.setConversation(conversation);
        interested.setUser(responder);
        interested.setRole(ParticipantRole.INTERESTED);
        participantRepository.saveAll(List.of(owner, interested));

        if (request.getStatus() == RequestStatus.OPEN) {
            notificationService.create(requesterId, NotificationType.REQUEST_RESPONSE,
                    "New response to your request",
                    "Someone responded to “" + request.getTitle() + "”. Open the conversation to reply.",
                    "/requests/" + request.getId(), request.getId(), false);
        }
        return summary(conversation, userId);
    }

    /** Sends a REQUEST_FULFILLED notification to everyone who responded. */
    @Transactional
    public void notifyRequestFulfilled(ResourceRequest request, UUID requesterId) {
        List<UUID> requestConversationIds = conversationRepository.findByRequestId(request.getId())
                .stream()
                .map(Conversation::getId)
                .toList();
        if (requestConversationIds.isEmpty()) {
            return;
        }
        for (ConversationParticipant participant
                : participantRepository.findByConversationIdIn(requestConversationIds)) {
            UUID participantId = participant.getUser().getId();
            if (participantId.equals(requesterId)) {
                continue;
            }
            notificationService.create(participantId, NotificationType.REQUEST_FULFILLED,
                    "Request you responded to was fulfilled",
                    "“" + request.getTitle() + "” was fulfilled by its requester.",
                    "/requests/" + request.getId(), request.getId(), false);
        }
    }

    @Transactional(readOnly = true)
    public Page<ConversationSummaryDto> list(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 30));
        Page<Conversation> conversations = conversationRepository.findForUser(
                userId, PageRequest.of(safePage, safeSize));
        List<UUID> ids = conversations.getContent().stream().map(Conversation::getId).toList();
        if (ids.isEmpty()) {
            return conversations.map(conversation -> summaryWithoutExtras(conversation, userId));
        }
        Map<UUID, List<ConversationParticipant>> participantsByConversation =
                participantRepository.findByConversationIdIn(ids).stream()
                        .collect(Collectors.groupingBy(p -> p.getConversation().getId()));
        Map<UUID, MessageDto> lastMessages = messageRepository.findLatestForConversations(ids).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), MessageDto::from));
        Map<UUID, Long> unread = new HashMap<>();
        for (ConversationParticipantRepository.UnreadRow row
                : participantRepository.unreadByConversationIds(userId, ids)) {
            unread.put(row.getConversationId(), row.getUnread());
        }
        return conversations.map(conversation -> summary(
                conversation,
                userId,
                participantsByConversation.getOrDefault(conversation.getId(), List.of()),
                lastMessages.get(conversation.getId()),
                unread.getOrDefault(conversation.getId(), 0L)
        ));
    }

    @Transactional(readOnly = true)
    public ConversationSummaryDto get(UUID userId, UUID conversationId) {
        Conversation conversation = conversationRepository.findDetailedById(conversationId)
                .orElseThrow(() -> ApiException.notFound("That conversation could not be found."));
        assertParticipant(conversationId, userId);
        return summary(conversation, userId);
    }

    @Transactional(readOnly = true)
    public MessagePageDto messages(UUID userId, UUID conversationId, Long beforeId, int size) {
        assertParticipant(conversationId, userId);
        int safeSize = Math.max(1, Math.min(size, 50));
        PageRequest pageable = PageRequest.of(0, safeSize);
        Page<Message> page = beforeId == null
                ? messageRepository.findByConversationIdOrderByIdDesc(conversationId, pageable)
                : messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(conversationId, beforeId, pageable);
        List<MessageDto> messages = new ArrayList<>(page.getContent().stream()
                .map(MessageDto::from)
                .toList());
        messages.sort(Comparator.comparingLong(MessageDto::id));
        Long nextBefore = messages.isEmpty() ? null : messages.get(0).id();
        return new MessagePageDto(messages, page.hasNext(), nextBefore);
    }

    @Transactional
    public MessageDto send(UUID userId, UUID conversationId, String rawBody) {
        ConversationParticipant participant = assertParticipant(conversationId, userId);
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty()) {
            throw ApiException.badRequest("EMPTY_MESSAGE", "Write a message before sending.");
        }
        if (body.length() > 4000) {
            throw ApiException.badRequest("MESSAGE_TOO_LONG", "Messages must be 4000 characters or fewer.");
        }
        Conversation conversation = participant.getConversation();
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(participant.getUser());
        message.setBody(body);
        messageRepository.saveAndFlush(message);
        conversation.setLastMessageAt(message.getCreatedAt() == null ? Instant.now() : message.getCreatedAt());
        conversationRepository.save(conversation);

        MessageDto dto = MessageDto.from(message);
        List<UUID> participantIds = participantRepository.findByConversationId(conversationId).stream()
                .map(p -> p.getUser().getId())
                .toList();
        eventPublisher.publishEvent(new MessageCreatedEvent(conversationId, dto, participantIds));
        return dto;
    }

    @Transactional
    public UnreadCountDto markRead(UUID userId, UUID conversationId, Long lastMessageId) {
        ConversationParticipant participant = assertParticipant(conversationId, userId);
        Message message = messageRepository.findById(lastMessageId)
                .orElseThrow(() -> ApiException.badRequest("MESSAGE_NOT_FOUND", "That message is not in this conversation."));
        if (!message.getConversation().getId().equals(conversationId)) {
            throw ApiException.badRequest("MESSAGE_NOT_FOUND", "That message is not in this conversation.");
        }
        Long current = participant.getLastReadMessageId();
        if (current == null || current < lastMessageId) {
            participant.setLastReadMessageId(lastMessageId);
            participantRepository.saveAndFlush(participant);
        }
        return new UnreadCountDto(participantRepository.unreadInConversation(conversationId, userId));
    }

    @Transactional(readOnly = true)
    public UnreadCountDto unreadTotal(UUID userId) {
        return new UnreadCountDto(participantRepository.unreadTotal(userId));
    }

    private ConversationParticipant assertParticipant(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("That conversation could not be found."));
    }

    private ConversationSummaryDto summary(Conversation conversation, UUID viewerId) {
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversation.getId());
        MessageDto lastMessage = messageRepository.findFirstByConversationIdOrderByIdDesc(conversation.getId())
                .map(MessageDto::from)
                .orElse(null);
        long unread = participantRepository.unreadInConversation(conversation.getId(), viewerId);
        return summary(conversation, viewerId, participants, lastMessage, unread);
    }

    private ConversationSummaryDto summary(Conversation conversation, UUID viewerId,
                                           List<ConversationParticipant> participants,
                                           MessageDto lastMessage, long unread) {
        SellerDto other = participants.stream()
                .filter(p -> !p.getUser().getId().equals(viewerId))
                .findFirst()
                .map(p -> SellerDto.from(p.getUser()))
                .orElse(null);
        Listing listing = conversation.getListing();
        if (listing != null) {
            String cover = listing.getImages().stream()
                    .findFirst()
                    .map(image -> image.getMedia().publicUrl())
                    .orElse(null);
            String listingTitle = listing.getBook() != null
                    ? listing.getBook().getTitle()
                    : listing.getTitle();
            String listingAuthor = listing.getBook() != null
                    ? listing.getBook().getAuthor()
                    : listing.getAuthor();
            return new ConversationSummaryDto(
                    conversation.getId(),
                    listing.getId(),
                    listingTitle,
                    listingAuthor,
                    cover,
                    listing.getPriceType().name(),
                    listing.getPrice(),
                    listing.getStatus().name(),
                    null,
                    null,
                    null,
                    other,
                    lastMessage,
                    unread,
                    conversation.getCreatedAt(),
                    conversation.getLastMessageAt()
            );
        }
        ResourceRequest request = conversation.getRequest();
        return new ConversationSummaryDto(
                conversation.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.getId(),
                request.getTitle(),
                request.getStatus().name(),
                other,
                lastMessage,
                unread,
                conversation.getCreatedAt(),
                conversation.getLastMessageAt()
        );
    }

    private ConversationSummaryDto summaryWithoutExtras(Conversation conversation, UUID viewerId) {
        return summary(conversation, viewerId, List.of(), null, 0L);
    }

    private String directKey(UUID listingId, UUID ownerId, UUID buyerId) {
        return listingId + ":" + directPair(ownerId, buyerId);
    }

    private String directPair(UUID first, UUID second) {
        UUID low = Comparator.<UUID>naturalOrder().compare(first, second) <= 0 ? first : second;
        UUID high = low.equals(first) ? second : first;
        return low + ":" + high;
    }
}
