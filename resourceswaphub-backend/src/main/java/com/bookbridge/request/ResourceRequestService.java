package com.bookbridge.request;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.category.CategoryService;
import com.bookbridge.category.ResourceCategory;
import com.bookbridge.communication.ChatService;
import com.bookbridge.communication.ConversationSummaryDto;
import com.bookbridge.listing.BookCondition;
import com.bookbridge.notification.NotificationService;
import com.bookbridge.notification.NotificationType;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class ResourceRequestService {
    static final int MAX_DAILY_REQUESTS = 50;
    static final int DEFAULT_EXPIRY_DAYS = 30;
    static final int MAX_EXPIRY_DAYS = 90;

    private final ResourceRequestRepository requestRepository;
    private final CategoryService categoryService;
    private final LocationService locationService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ChatService chatService;

    public ResourceRequestService(ResourceRequestRepository requestRepository, CategoryService categoryService,
                                  LocationService locationService, UserService userService,
                                  NotificationService notificationService, ChatService chatService) {
        this.requestRepository = requestRepository;
        this.categoryService = categoryService;
        this.locationService = locationService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.chatService = chatService;
    }

    @Transactional
    public ResourceRequestDto create(UUID userId, ResourceRequestCreateRequest request) {
        if (!underDailyLimit(userId)) {
            throw ApiException.badRequest("RATE_LIMITED",
                    "You have reached the daily limit for creating resource requests. Try again tomorrow.");
        }
        User requester = userService.require(userId);
        if (!requester.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before requesting a resource.");
        }
        ResourceCategory category = categoryService.requireById(requireCategory(request.categoryId()));

        ResourceRequest entity = new ResourceRequest();
        entity.setRequester(requester);
        entity.setCategory(category);
        applyFields(entity, request.title(), request.description(), request.subject(), request.author(),
                request.edition(), request.academicLevel(), request.preferredCondition(), request.priceMin(),
                request.priceMax(), Boolean.TRUE.equals(request.freeOnly()), ShapedLocation.of(request), requester);
        entity.setStatus(RequestStatus.OPEN);
        int days = request.expiresInDays() == null ? DEFAULT_EXPIRY_DAYS
                : Math.max(1, Math.min(request.expiresInDays(), MAX_EXPIRY_DAYS));
        entity.setExpiresAt(Instant.now().plus(Duration.ofDays(days).toDays(), ChronoUnit.DAYS));
        requestRepository.saveAndFlush(entity);
        return toDto(entity, requester.getId(), List.of());
    }

    @Transactional
    public ResourceRequestDto update(UUID userId, UUID requestId, ResourceRequestUpdateRequest request) {
        ResourceRequest entity = ownedOpenRequest(requestId, userId);
        if (request.title() != null) {
            entity.setTitle(trimmed(request.title(), 200));
        }
        if (request.description() != null) {
            entity.setDescription(blankToNull(request.description()));
        }
        if (request.categoryId() != null) {
            entity.setCategory(categoryService.requireById(request.categoryId()));
        }
        if (request.subject() != null) {
            entity.setSubject(blankToNull(request.subject()));
        }
        if (request.author() != null) {
            entity.setAuthor(blankToNull(request.author()));
        }
        if (request.edition() != null) {
            entity.setEdition(blankToNull(request.edition()));
        }
        if (request.academicLevel() != null) {
            entity.setAcademicLevel(blankToNull(request.academicLevel()));
        }
        if (request.clearCondition() != null && request.clearCondition()) {
            entity.setPreferredCondition(null);
        } else if (request.preferredCondition() != null) {
            entity.setPreferredCondition(request.preferredCondition());
        }
        if (request.freeOnly() != null) {
            entity.setFreeOnly(request.freeOnly());
        }
        if (request.priceMin() != null || request.priceMax() != null || request.freeOnly() != null) {
            validatePriceRange(entity.isFreeOnly(),
                    request.priceMin() != null ? request.priceMin() : entity.getPriceMin(),
                    request.priceMax() != null ? request.priceMax() : entity.getPriceMax());
            entity.setPriceMin(request.priceMin() != null ? request.priceMin() : entity.getPriceMin());
            entity.setPriceMax(request.priceMax() != null ? request.priceMax() : entity.getPriceMax());
        }
        if (request.countryId() != null || request.stateId() != null || request.cityId() != null
                || (request.city() != null && !request.city().isBlank())) {
            applyLocation(entity, ShapedLocation.ofUpdate(request, entity.getRequester()), entity.getRequester());
        }
        if (request.expiresInDays() != null) {
            int days = Math.max(1, Math.min(request.expiresInDays(), MAX_EXPIRY_DAYS));
            entity.setExpiresAt(entity.getCreatedAt().plus(days, ChronoUnit.DAYS));
        }
        requestRepository.saveAndFlush(entity);
        return toDto(entity, userId, List.of());
    }

    @Transactional
    public ResourceRequestDto cancel(UUID userId, UUID requestId) {
        ResourceRequest entity = ownedOpenRequest(requestId, userId);
        requestRepository.cancelIfOpen(entity.getId());
        entity.setStatus(RequestStatus.CANCELLED);
        entity.setCancelledAt(Instant.now());
        return toDto(entity, userId, List.of());
    }

    @Transactional
    public ResourceRequestDto fulfill(UUID userId, UUID requestId) {
        ResourceRequest entity = ownedOpenRequest(requestId, userId);
        requestRepository.fulfillIfOpen(entity.getId());
        entity.setStatus(RequestStatus.FULFILLED);
        entity.setFulfilledAt(Instant.now());
        notifyResponders(entity, userId);
        return toDto(entity, userId, List.of());
    }

    @Transactional
    public void delete(UUID userId, UUID requestId) {
        ResourceRequest entity = ownedRequest(requestId, userId);
        requestRepository.delete(entity);
    }

    @Transactional
    public ResourceRequestDto get(UUID requestId, UUID viewerId) {
        // Lazy expiry writes, so this read cannot be declared readOnly: a read-only
        // transaction turns the routine housekeeping into a 500.
        expireDue();
        ResourceRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("That request could not be found."));
        return toDto(entity, viewerId, List.of());
    }

    @Transactional
    public PageResponse<ResourceRequestDto> browse(BrowseQuery query) {
        expireDue();
        int page = Math.max(query.page(), 0);
        int size = Math.max(1, Math.min(query.size(), 48));
        String q = query.q() == null ? "" : query.q().trim();
        boolean hasQuery = !q.isEmpty();
        String tsQuery = hasQuery ? prefixTsQuery(q) : "bookbridge";
        String likeQuery = hasQuery ? "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%" : "%";
        UUID categoryId = query.categoryId() == null ? null
                : categoryService.requireById(query.categoryId()).getId();
        String status = query.status() == null ? null : query.status().name();
        UUID requesterId = query.requesterId();
        boolean includeStatus = status != null || requesterId != null;

        Page<ResourceRequest> result = requestRepository.browse(
                includeStatus,
                Instant.now(),
                status,
                hasQuery,
                tsQuery,
                likeQuery,
                categoryId,
                blankToNull(query.subject()),
                blankToNull(query.academicLevel()),
                query.freeOnly(),
                query.minPrice(),
                query.maxPrice(),
                validated(query.stateId(), locationService::requireState),
                validated(query.cityId(), locationService::requireCity),
                query.preferredCondition() == null ? null : query.preferredCondition().name(),
                requesterId,
                PageRequest.of(page, size)
        );
        List<ResourceRequestDto> content = result.getContent().stream()
                .map(entity -> toDto(entity, null, List.of()))
                .toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(),
                result.getTotalPages(), result.hasNext());
    }

    @Transactional
    public PageResponse<ResourceRequestDto> mine(UUID userId, RequestStatus status, int page, int size) {
        expireDue();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<ResourceRequest> result = status == null
                ? requestRepository.findByRequesterIdOrderByCreatedAtDesc(userId, pageable)
                : requestRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        List<ResourceRequestDto> content = result.getContent().stream()
                .map(entity -> toDto(entity, userId, List.of()))
                .toList();
        return new PageResponse<>(content, Math.max(0, page), Math.max(1, Math.min(size, 48)),
                result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    @Transactional
    public void expireDue() {
        requestRepository.expireDueRequests(Instant.now());
    }

    private ResourceRequest ownedOpenRequest(UUID requestId, UUID userId) {
        ResourceRequest entity = ownedRequest(requestId, userId);
        if (entity.getStatus() != RequestStatus.OPEN) {
            throw ApiException.badRequest("REQUEST_NOT_OPEN",
                    "Only open requests can be changed.");
        }
        return entity;
    }

    private ResourceRequest ownedRequest(UUID requestId, UUID userId) {
        ResourceRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("That request could not be found."));
        if (!entity.getRequester().getId().equals(userId)) {
            throw ApiException.notFound("That request could not be found.");
        }
        return entity;
    }

    private void notifyResponders(ResourceRequest entity, UUID actingUserId) {
        notificationService.create(entity.getRequester().getId(), NotificationType.REQUEST_FULFILLED,
                "Your request was fulfilled",
                "You marked “" + entity.getTitle() + "” as fulfilled.",
                "/requests/" + entity.getId(), entity.getId(), false);
        chatService.notifyRequestFulfilled(entity, entity.getRequester().getId());
    }

    /** Chat response: reuses the existing conversation system, one thread per (request, pair). */
    @Transactional
    public ConversationSummaryDto respond(UUID userId, UUID requestId) {
        ResourceRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("That request could not be found."));
        if (!entity.isOpenForResponses(Instant.now())) {
            throw ApiException.badRequest("REQUEST_NOT_OPEN",
                    "This request is no longer accepting responses.");
        }
        if (entity.getRequester().getId().equals(userId)) {
            throw ApiException.badRequest("OWN_REQUEST", "This is your own request.");
        }
        return chatService.startForRequest(userId, entity);
    }

    private void applyFields(ResourceRequest entity, String title, String description, String subject,
                              String author, String edition, String academicLevel,
                              BookCondition preferredCondition, BigDecimal priceMin, BigDecimal priceMax,
                              boolean freeOnly, ShapedLocation location, User requester) {
        entity.setTitle(trimmed(title, 200));
        entity.setDescription(blankToNull(description));
        entity.setSubject(blankToNull(subject));
        entity.setAuthor(blankToNull(author));
        entity.setEdition(blankToNull(edition));
        entity.setAcademicLevel(blankToNull(academicLevel));
        entity.setPreferredCondition(preferredCondition);
        validatePriceRange(freeOnly, priceMin, priceMax);
        entity.setFreeOnly(freeOnly);
        entity.setPriceMin(priceMin == null ? null : priceMin.setScale(2, RoundingMode.HALF_UP));
        entity.setPriceMax(priceMax == null ? null : priceMax.setScale(2, RoundingMode.HALF_UP));
        applyLocation(entity, location, requester);
    }

    private void applyLocation(ResourceRequest entity, ShapedLocation shaped, User requester) {
        LocationService.ResolvedLocation resolved = locationService.resolve(
                shaped.countryId(), shaped.stateId(), shaped.cityId(),
                shaped.city() != null && !shaped.city().isBlank() ? shaped.city() : requester.getCity(),
                requester.getState(), requester.getCountry());
        if (resolved.cityName() == null) {
            throw ApiException.badRequest("CITY_REQUIRED", "Choose the city you are requesting for.");
        }
        entity.setCityName(resolved.cityName());
        entity.setCountryId(resolved.countryId());
        entity.setStateId(resolved.stateId());
        entity.setCityId(resolved.cityId());
    }

    private void validatePriceRange(boolean freeOnly, BigDecimal priceMin, BigDecimal priceMax) {
        if (freeOnly && (priceMin != null || priceMax != null)) {
            throw ApiException.badRequest("INVALID_PRICE",
                    "A free-only request cannot carry a price range.");
        }
        if (priceMin != null && priceMin.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_PRICE", "Price must be greater than zero.");
        }
        if (priceMax != null && priceMax.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_PRICE", "Price must be greater than zero.");
        }
        if (priceMin != null && priceMax != null && priceMin.compareTo(priceMax) > 0) {
            throw ApiException.badRequest("INVALID_PRICE", "Minimum price cannot exceed the maximum.");
        }
    }

    private UUID requireCategory(UUID categoryId) {
        if (categoryId == null) {
            throw ApiException.badRequest("CATEGORY_REQUIRED",
                    "Choose the category of resource you need.");
        }
        return categoryId;
    }

    private boolean underDailyLimit(UUID userId) {
        return requestRepository.countByRequesterIdAndCreatedAtAfter(
                userId, Instant.now().minus(1, ChronoUnit.DAYS)) < MAX_DAILY_REQUESTS;
    }

    private ResourceRequestDto toDto(ResourceRequest entity, UUID viewerId,
                                     List<UUID> conversationIds) {
        return new ResourceRequestDto(
                entity.getId(),
                RequesterDto.from(entity.getRequester()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCategory() == null
                        ? null : com.bookbridge.category.ResourceCategoryDto.from(entity.getCategory()),
                entity.getSubject(),
                entity.getAuthor(),
                entity.getEdition(),
                entity.getAcademicLevel(),
                entity.getPreferredCondition() == null ? null : entity.getPreferredCondition().name(),
                entity.getPriceMin(),
                entity.getPriceMax(),
                entity.isFreeOnly(),
                locationService.describe(entity.getCountryId(), entity.getStateId(), entity.getCityId()),
                entity.getCityName(),
                entity.getStatus().name(),
                entity.getExpiresAt(),
                entity.getFulfilledAt(),
                entity.getCancelledAt(),
                entity.getExpiredAt(),
                entity.getCreatedAt(),
                conversationIds
        );
    }

    private UUID validated(UUID id, java.util.function.Consumer<UUID> require) {
        if (id != null) {
            require.accept(id);
        }
        return id;
    }

    private String trimmed(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("TITLE_REQUIRED", "Describe what you are looking for.");
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String prefixTsQuery(String value) {
        String[] tokens = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .trim()
                .split("\\s+");
        StringBuilder builder = new StringBuilder();
        int added = 0;
        for (String token : tokens) {
            if (token.isBlank() || added >= 6) {
                continue;
            }
            String safe = token.length() > 40 ? token.substring(0, 40) : token;
            if (!builder.isEmpty()) {
                builder.append(" & ");
            }
            builder.append(safe).append(":*");
            added++;
        }
        return builder.isEmpty() ? "bookbridge" : builder.toString();
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record ShapedLocation(UUID countryId, UUID stateId, UUID cityId, String city) {

        static ShapedLocation of(ResourceRequestCreateRequest request) {
            return new ShapedLocation(request.countryId(), request.stateId(), request.cityId(), request.city());
        }

        static ShapedLocation ofUpdate(ResourceRequestUpdateRequest request, User requester) {
            return new ShapedLocation(request.countryId(), request.stateId(), request.cityId(),
                    request.city() != null && !request.city().isBlank()
                            ? request.city()
                            : requester.getCity());
        }
    }

    public record BrowseQuery(
            String q,
            UUID categoryId,
            String subject,
            String academicLevel,
            Boolean freeOnly,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            RequestStatus status,
            BookCondition preferredCondition,
            UUID stateId,
            UUID cityId,
            UUID requesterId,
            int page,
            int size
    ) {
    }
}
