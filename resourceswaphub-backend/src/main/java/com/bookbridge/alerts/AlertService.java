package com.bookbridge.alerts;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.bookbridge.category.CategoryService;
import com.bookbridge.category.ResourceCategory;
import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingPublishedEvent;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.PriceType;
import com.bookbridge.location.LocationService;
import com.bookbridge.notification.NotificationService;
import com.bookbridge.notification.NotificationType;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

/**
 * Alert CRUD plus synchronous alert matching. A new listing is matched against
 * active alerts inside the publishing transaction (BEFORE_COMMIT listener), so
 * the notification commits or rolls back together with the listing. No queues,
 * no schedulers.
 */
@Service
public class AlertService {
    private static final int MAX_ALERTS = 30;

    private final ResourceAlertRepository alertRepository;
    private final CategoryService categoryService;
    private final LocationService locationService;
    private final UserService userService;
    private final ListingRepository listingRepository;
    private final NotificationService notificationService;

    public AlertService(ResourceAlertRepository alertRepository, CategoryService categoryService,
                        LocationService locationService, UserService userService,
                        ListingRepository listingRepository, NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.categoryService = categoryService;
        this.locationService = locationService;
        this.userService = userService;
        this.listingRepository = listingRepository;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------------

    @Transactional
    public ResourceAlertDto create(UUID userId, ResourceAlertRequest request) {
        User user = userService.require(userId);
        if (alertRepository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_ALERTS) {
            throw ApiException.badRequest("ALERT_LIMIT",
                    "You can keep at most " + MAX_ALERTS + " resource alerts.");
        }
        ResourceAlert alert = new ResourceAlert();
        alert.setUser(user);
        applyFields(alert, request, user);
        assertUnique(userId, alert);
        alertRepository.saveAndFlush(alert);
        return ResourceAlertDto.from(alert);
    }

    @Transactional
    public ResourceAlertDto update(UUID userId, UUID alertId, ResourceAlertRequest request) {
        ResourceAlert alert = owned(userId, alertId);
        applyFields(alert, request, alert.getUser());
        assertUnique(userId, alert);
        if (request.enabled() != null) {
            alert.setEnabled(request.enabled());
        }
        alertRepository.saveAndFlush(alert);
        return ResourceAlertDto.from(alert);
    }

    @Transactional
    public void delete(UUID userId, UUID alertId) {
        ResourceAlert alert = owned(userId, alertId);
        alertRepository.delete(alert);
    }

    @Transactional(readOnly = true)
    public List<ResourceAlertDto> mine(UUID userId) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(ResourceAlertDto::from)
                .toList();
    }

    // ------------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------------

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onListingPublished(ListingPublishedEvent event) {
        Listing listing = listingRepository.findWithDetailsById(event.listingId()).orElse(null);
        if (listing == null || listing.getOwner() == null) {
            return;
        }
        UUID ownerId = listing.getOwner().getId();
        String haystack = String.join(" \n",
                nullSafe(listing.getTitle()),
                nullSafe(listing.getNotes()),
                nullSafe(listing.getSubject()),
                nullSafe(listing.getAuthor())).toLowerCase(Locale.ROOT);

        for (ResourceAlert alert : alertRepository.findAll()) {
            if (!alert.isEnabled() || alert.getUser() == null
                    || alert.getUser().getId().equals(ownerId)
                    || !matches(alert, listing, haystack)) {
                continue;
            }
            // At most one alert-match notification per user and listing ever.
            if (notificationService.exists(NotificationType.LISTING_ALERT_MATCH,
                    alert.getUser().getId(), listing.getId())) {
                continue;
            }
            notificationService.create(alert.getUser().getId(), NotificationType.LISTING_ALERT_MATCH,
                    "New resource matches your alert",
                    describeMatch(alert, listing),
                    "/books/" + listing.getId(), listing.getId(), false);
        }
    }

    boolean matches(ResourceAlert alert, Listing listing, String haystack) {
        if (alert.getKeyword() != null) {
            String keyword = alert.getKeyword().trim().toLowerCase(Locale.ROOT);
            if (!keyword.isEmpty() && !haystack.contains(keyword)) {
                return false;
            }
        }
        if (alert.getCategory() != null
                && (listing.getCategory() == null
                        || !alert.getCategory().getId().equals(listing.getCategory().getId()))) {
            return false;
        }
        if (alert.getSubject() != null) {
            String listingSubject = listing.getSubject() != null ? listing.getSubject()
                    : listing.getBook() != null ? listing.getBook().getSubject() : null;
            if (listingSubject == null
                    || !listingSubject.trim().equalsIgnoreCase(alert.getSubject().trim())) {
                return false;
            }
        }
        if (alert.getAcademicLevel() != null
                && (listing.getAcademicLevel() == null
                        || !listing.getAcademicLevel().trim().equalsIgnoreCase(alert.getAcademicLevel().trim()))) {
            return false;
        }
        if (alert.getStateId() != null && !alert.getStateId().equals(listing.getStateId())) {
            return false;
        }
        if (alert.getCityId() != null && !alert.getCityId().equals(listing.getCityId())) {
            return false;
        }
        return !alert.isFreeOnly() || listing.getPriceType() == PriceType.FREE;
    }

    private String describeMatch(ResourceAlert alert, Listing listing) {
        String title = listing.getTitle() != null ? listing.getTitle()
                : listing.getBook() != null ? listing.getBook().getTitle() : "A new resource";
        String where = listing.getCity() != null ? " in " + listing.getCity() : "";
        return "“" + title + "”" + where + " matches the alert you created.";
    }

    // ------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------

    private void applyFields(ResourceAlert alert, ResourceAlertRequest request, User user) {
        if (request.keyword() != null) {
            String keyword = request.keyword().trim();
            alert.setKeyword(keyword.isEmpty() ? null : keyword);
        }
        if (request.categoryId() != null) {
            ResourceCategory category = categoryService.requireById(request.categoryId());
            alert.setCategory(category);
        }
        if (request.subject() != null) {
            String subject = request.subject().trim();
            alert.setSubject(subject.isEmpty() ? null : subject);
        }
        if (request.academicLevel() != null) {
            String level = request.academicLevel().trim();
            alert.setAcademicLevel(level.isEmpty() ? null : level);
        }
        if (request.stateId() != null) {
            locationService.requireState(request.stateId());
            alert.setStateId(request.stateId());
        }
        if (request.cityId() != null) {
            locationService.requireCity(request.cityId());
            alert.setCityId(request.cityId());
        }
        if (request.freeOnly() != null) {
            alert.setFreeOnly(request.freeOnly());
        }
        boolean hasCriteria = alert.getKeyword() != null || alert.getCategory() != null
                || alert.getSubject() != null || alert.getAcademicLevel() != null
                || alert.getStateId() != null || alert.getCityId() != null || alert.isFreeOnly();
        if (!hasCriteria) {
            throw ApiException.badRequest("ALERT_EMPTY",
                    "Add at least one filter so the alert can match something.");
        }
    }

    private void assertUnique(UUID userId, ResourceAlert alert) {
        boolean duplicate = alertRepository
                .existsByUserIdAndKeywordAndCategoryIdAndSubjectAndAcademicLevelAndStateIdAndCityIdAndFreeOnly(
                        userId, alert.getKeyword(),
                        alert.getCategory() == null ? null : alert.getCategory().getId(),
                        alert.getSubject(), alert.getAcademicLevel(),
                        alert.getStateId(), alert.getCityId(), alert.isFreeOnly());
        if (duplicate) {
            throw ApiException.conflict("DUPLICATE_ALERT",
                    "You already have this exact alert.");
        }
    }

    private ResourceAlert owned(UUID userId, UUID alertId) {
        return alertRepository.findByIdAndUserId(alertId, userId)
                .orElseThrow(() -> ApiException.notFound("That alert could not be found."));
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
