package com.bookbridge.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookbridge.catalog.Book;
import com.bookbridge.catalog.BookRepository;
import com.bookbridge.category.CategoryService;
import com.bookbridge.category.ResourceCategory;
import com.bookbridge.collection.CollectionService;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.media.MediaAsset;
import com.bookbridge.shared.media.MediaPurpose;
import com.bookbridge.shared.media.MediaService;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class ListingService {
    static final int MAX_BULK_ITEMS = 20;
    static final int MAX_DAILY_LISTINGS = 100;

    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingStatusHistoryRepository statusHistoryRepository;
    private final BookRepository bookRepository;
    private final MediaService mediaService;
    private final UserService userService;
    private final LocationService locationService;
    private final CategoryService categoryService;
    private final ListingMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final CollectionService collectionService;

    public ListingService(ListingRepository listingRepository, ListingImageRepository listingImageRepository,
                          ListingStatusHistoryRepository statusHistoryRepository, BookRepository bookRepository,
                          MediaService mediaService, UserService userService, LocationService locationService,
                          CategoryService categoryService, ListingMapper mapper, TransactionTemplate transactionTemplate,
                          ApplicationEventPublisher eventPublisher,
                          CollectionService collectionService) {
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.bookRepository = bookRepository;
        this.mediaService = mediaService;
        this.userService = userService;
        this.locationService = locationService;
        this.categoryService = categoryService;
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.collectionService = collectionService;
    }

    // ------------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------------

    @Transactional
    public ListingDto create(UUID userId, ListingCreateRequest request) {
        if (request.clientRef() != null
                && listingRepository.findByOwnerIdAndClientRef(userId, request.clientRef()).isPresent()) {
            throw ApiException.conflict("DUPLICATE_CLIENT_REF",
                    "This entry was already published. Refresh to see the existing listing.");
        }
        if (!underDailyLimit(userId)) {
            throw ApiException.badRequest("RATE_LIMITED",
                    "You have reached the daily limit for creating listings. Try again tomorrow.");
        }
        User owner = userService.require(userId);
        if (!owner.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before listing a resource.");
        }

        // An absent flag means false: clients written before the rights field existed must keep
        // working, and Jackson 3 refuses to bind a missing value onto a primitive record component.
        ResolvedShape shape = resolveShape(owner, request.bookId(), request.categoryId(), request.title(),
                request.medium(), request.condition(), request.externalUrl(), request.digitalMediaId(),
                Boolean.TRUE.equals(request.ownerRightsConfirmed()));

        Listing listing = new Listing();
        listing.setOwner(owner);
        applyMetadata(listing, shape, request.subject(), request.academicLevel(), request.board(),
                request.author(), request.publisher(), request.edition(), request.publicationYear());
        applyOfferFields(listing, request.priceType(), request.price(), request.locality(), request.notes(),
                location(ShapedLocation.of(request), owner));
        attachImages(listing, request.imageIds(), userId, false);
        listing.setStatus(ListingStatus.AVAILABLE);
        listing.setClientRef(trimToNull(request.clientRef()));
        listingRepository.saveAndFlush(listing);
        if (request.collectionId() != null) {
            collectionService.attachForListingCreate(userId, request.collectionId(), listing.getId());
        }
        recordTransition(listing, null, ListingStatus.AVAILABLE, owner, "USER");
        eventPublisher.publishEvent(new ListingPublishedEvent(listing.getId()));
        return mapper.toDto(listing);
    }

    // ------------------------------------------------------------------------
    // Bulk creation: each entry commits in its own transaction so one bad entry
    // cannot roll back the valid ones; failures are reported per entry.
    // ------------------------------------------------------------------------

    public record BulkItemResult(UUID id, String clientRef, boolean success, String errorCode, String errorMessage) {
    }

    /**
     * Bulk creation. Each entry runs in its own transaction (via a
     * TransactionTemplate because self-invocation would bypass @Transactional)
     * so one malformed entry cannot roll back the valid ones.
     */
    public List<BulkItemResult> createBulk(UUID userId, List<ListingCreateRequest> requests) {
        List<ListingCreateRequest> items = requests == null ? List.of() : requests;
        if (items.isEmpty()) {
            throw ApiException.badRequest("BULK_EMPTY", "Add at least one resource to publish.");
        }
        if (items.size() > MAX_BULK_ITEMS) {
            throw ApiException.badRequest("BULK_TOO_LARGE",
                    "Publish at most " + MAX_BULK_ITEMS + " resources per batch.");
        }
        List<BulkItemResult> results = new ArrayList<>(items.size());
        for (ListingCreateRequest item : items) {
            results.add(createBulkItem(userId, item));
        }
        return results;
    }

    private BulkItemResult createBulkItem(UUID userId, ListingCreateRequest request) {
        try {
            ListingDto dto = transactionTemplate.execute(status -> create(userId, request));
            return new BulkItemResult(dto.id(), dto.clientRef(), true, null, null);
        } catch (ApiException ex) {
            return new BulkItemResult(null, trimToNull(request.clientRef()), false, ex.getCode(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------------

    @Transactional
    public ListingDto update(UUID userId, UUID listingId, ListingUpdateRequest request) {
        Listing listing = ownedListing(listingId, userId);
        User owner = listing.getOwner();

        ListingMedium targetMedium = request.medium() == null
                ? (listing.getMedium() == null ? ListingMedium.PHYSICAL : listing.getMedium())
                : request.medium();
        UUID categoryId = request.categoryId() != null ? request.categoryId()
                : (listing.getCategory() != null ? listing.getCategory().getId() : null);
        boolean digitalSwitched = targetMedium == ListingMedium.DIGITAL_FILE
                && (listing.getExternalUrl() != null || listing.getDigitalMedia() == null);
        boolean rightsConfirmed = request.ownerRightsConfirmed() != null
                ? request.ownerRightsConfirmed()
                : listing.isOwnerRightsConfirmed();
        UUID digitalId = request.digitalMediaId() != null ? request.digitalMediaId()
                : (listing.getDigitalMedia() == null || digitalSwitched ? null : listing.getDigitalMedia().getId());
        boolean clearsDigitalId = request.medium() != null
                && request.medium() != ListingMedium.DIGITAL_FILE;
        if (clearsDigitalId) {
            digitalId = null;
        }
        String externalUrl = targetMedium == ListingMedium.EXTERNAL_LINK
                ? (request.externalUrl() != null ? request.externalUrl() : listing.getExternalUrl())
                : null;

        ResolvedShape shape = resolveShape(owner,
                listing.getBook() == null ? null : listing.getBook().getId(),
                categoryId, request.title() != null ? request.title() : listing.getTitle(),
                targetMedium, request.condition() != null ? request.condition() : listing.getCondition(),
                externalUrl, digitalId, rightsConfirmed);

        applyMetadata(listing, shape, request.subject() != null ? request.subject() : listing.getSubject(),
                request.academicLevel() != null ? request.academicLevel() : listing.getAcademicLevel(),
                request.board() != null ? request.board() : listing.getBoard(),
                request.author() != null ? request.author() : listing.getAuthor(),
                request.publisher() != null ? request.publisher() : listing.getPublisher(),
                request.edition() != null ? request.edition() : listing.getEdition(),
                request.publicationYear() != null ? request.publicationYear()
                        : (listing.getPublicationYear() == null ? null
                                : listing.getPublicationYear().intValue()));

        PriceType priceType = request.priceType() == null ? listing.getPriceType() : request.priceType();
        BigDecimal price = request.priceType() == null ? listing.getPrice() : request.price();
        applyOfferFields(listing, priceType, price, request.locality(), request.notes(),
                location(ShapedLocation.ofUpdate(owner, request), owner));

        if (request.imageIds() != null) {
            listing.getImages().clear();
            listingImageRepository.flush();
            attachImages(listing, request.imageIds(), userId, true);
        }
        listing.setOwnerRightsConfirmed(shape.ownerRightsConfirmed());
        if (shape.digitalMediaId() != null) {
            MediaAsset asset = mediaService.getRequired(shape.digitalMediaId());
            listing.setDigitalMedia(asset);
            listing.setFileFormat(fileExtension(asset));
            listing.setFileSizeBytes(asset.getSizeBytes());
        } else {
            listing.setDigitalMedia(null);
            listing.setFileFormat(null);
            listing.setFileSizeBytes(null);
        }
        listing.setExternalUrl(shape.externalUrl());
        listingRepository.saveAndFlush(listing);
        return mapper.toDto(listing);
    }

    // ------------------------------------------------------------------------
    // Status lifecycle
    // ------------------------------------------------------------------------

    @Transactional
    public ListingDto updateStatus(UUID userId, UUID listingId, ListingStatus target) {
        Listing listing = ownedListing(listingId, userId);
        transition(listing, target, listing.getOwner(), "USER");
        listingRepository.saveAndFlush(listing);
        return mapper.toDto(listing);
    }

    /** Single entry point for every status change, including automated ones. */
    public void transition(Listing listing, ListingStatus target, User actor, String source) {
        if (listing.getStatus() == target) {
            return;
        }
        assertAllowedTransition(listing.getStatus(), target);
        ListingStatus previous = listing.getStatus();
        listing.setStatus(target);
        listingRepository.save(listing);
        recordTransition(listing, previous, target, actor, source);
        eventPublisher.publishEvent(new ListingStatusChangedEvent(listing.getId(), previous, target));
        if (target == ListingStatus.AVAILABLE) {
            eventPublisher.publishEvent(new ListingPublishedEvent(listing.getId()));
        }
    }

    // ------------------------------------------------------------------------
    // Read paths
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ListingDto getForViewer(UUID listingId, UUID viewerId) {
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        boolean owner = listing.getOwner().getId().equals(viewerId);
        if (!owner && (listing.getStatus() == ListingStatus.WITHDRAWN
                || listing.getModerationState() == ModerationState.REMOVED)) {
            throw ApiException.notFound("That listing could not be found.");
        }
        return mapper.toDto(listing);
    }

    @Transactional(readOnly = true)
    public Page<ListingDto> myListings(UUID userId, ListingStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Listing> result = status == null
                ? listingRepository.findByOwnerId(userId, pageable)
                : listingRepository.findByOwnerIdAndStatus(userId, status, pageable);
        LocationService.LocationIndex index = mapper.indexFor(result.getContent());
        return result.map(listing -> mapper.toDto(listing, index));
    }

    /**
     * Authorized access to a digital listing's stored file. Withdrawn or
     * moderated content is withheld even from authenticated users; the owner
     * always retains access to their own file.
     */
    @Transactional(readOnly = true)
    public MediaAsset digitalMediaForViewer(UUID listingId, UUID viewerId) {
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (listing.getDigitalMedia() == null) {
            throw ApiException.notFound("That listing has no attached file.");
        }
        boolean owner = listing.getOwner().getId().equals(viewerId);
        if (!owner && listing.getModerationState() != ModerationState.NONE) {
            throw ApiException.notFound("That listing could not be found.");
        }
        return listing.getDigitalMedia();
    }

    @Transactional(readOnly = true)
    public Listing requireAvailableForConversation(UUID listingId, UUID requesterId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (listing.getOwner().getId().equals(requesterId)) {
            throw ApiException.badRequest("OWN_LISTING", "This is your own listing.");
        }
        if (listing.getStatus() != ListingStatus.AVAILABLE
                || listing.getModerationState() != ModerationState.NONE) {
            throw ApiException.badRequest("LISTING_UNAVAILABLE", "This resource is no longer available.");
        }
        return listing;
    }

    // ------------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------------

    /** Validated combination of catalogue book, category and medium for a listing. */
    private record ResolvedShape(ResourceCategory category, Book book, String title, BookCondition condition,
                                 ListingMedium medium, String externalUrl, UUID digitalMediaId,
                                 boolean ownerRightsConfirmed) {
    }

    private record ShapedLocation(UUID countryId, UUID stateId, UUID cityId, String city) {

        static ShapedLocation of(ListingCreateRequest request) {
            return new ShapedLocation(request.countryId(), request.stateId(), request.cityId(), request.city());
        }

        static ShapedLocation ofUpdate(User owner, ListingUpdateRequest request) {
            return new ShapedLocation(request.countryId() != null ? request.countryId() : owner.getCountryId(),
                    request.stateId() != null ? request.stateId() : owner.getStateId(),
                    request.cityId() != null ? request.cityId() : owner.getCityId(),
                    request.city() != null && !request.city().isBlank()
                            ? request.city()
                            : owner.getCity());
        }
    }

    private ResolvedShape resolveShape(User owner, UUID bookId, UUID categoryId, String title,
                                       ListingMedium mediumRequest, BookCondition condition,
                                       String externalUrl, UUID digitalMediaId, boolean ownerRightsConfirmed) {
        ListingMedium medium = mediumRequest == null ? ListingMedium.PHYSICAL : mediumRequest;

        if (digitalMediaId != null && medium != ListingMedium.DIGITAL_FILE) {
            throw ApiException.badRequest("DIGITAL_CONFLICT",
                    "A file can only be attached to a digital-file listing.");
        }

        ResourceCategory category = categoryId == null ? null : categoryService.requireById(categoryId);

        Book book = null;
        String effectiveTitle = null;
        if (bookId != null) {
            book = bookRepository.findById(bookId)
                    .orElseThrow(() -> ApiException.badRequest("BOOK_NOT_FOUND", "Choose a catalogue book."));
            if (category != null && !category.isBookRequired()) {
                throw ApiException.badRequest("CATEGORY_BOOK_CONFLICT",
                        "This category does not accept catalogue books.");
            }
        } else {
            if (category == null) {
                throw ApiException.badRequest("CATEGORY_REQUIRED",
                        "Choose a resource category for a non-book resource.");
            }
            if (category.isBookRequired()) {
                throw ApiException.badRequest("BOOK_REQUIRED",
                        "This category lists catalogue books. Pick the book from the catalogue.");
            }
            String trimmed = title == null ? "" : title.trim();
            if (trimmed.isEmpty()) {
                throw ApiException.badRequest("TITLE_REQUIRED", "Give your resource a title.");
            }
            if (trimmed.length() > 255) {
                throw ApiException.badRequest("TITLE_TOO_LONG", "Keep the title under 255 characters.");
            }
            effectiveTitle = trimmed;
        }

        boolean digital = medium != ListingMedium.PHYSICAL;
        if (digital) {
            if (category != null && !category.isAllowsDigital()) {
                throw ApiException.badRequest("DIGITAL_NOT_ALLOWED",
                        "This category does not support digital resources. Choose a physical handover.");
            }
            if (!ownerRightsConfirmed) {
                throw ApiException.badRequest("RIGHTS_CONFIRMATION_REQUIRED",
                        "Confirm that you own this content or have permission to share it.");
            }
        }

        String effectiveExternalUrl = null;
        UUID effectiveDigitalId = null;
        BookCondition effectiveCondition = null;
        switch (medium) {
            case PHYSICAL -> {
                if (externalUrl != null) {
                    throw ApiException.badRequest("DIGITAL_CONFLICT",
                            "External links belong to digital listings.");
                }
                effectiveCondition = condition;
                if (effectiveCondition == null) {
                    throw ApiException.badRequest("CONDITION_REQUIRED", "Choose the condition of the resource.");
                }
            }
            case DIGITAL_FILE -> {
                if (digitalMediaId == null) {
                    throw ApiException.badRequest("DIGITAL_FILE_REQUIRED",
                            "Attach the resource file you want to share.");
                }
                MediaAsset asset = mediaService.getRequired(digitalMediaId);
                if (!asset.getOwner().getId().equals(owner.getId())) {
                    throw ApiException.forbidden("You can only share files you uploaded.");
                }
                if (asset.getPurpose() != MediaPurpose.DOCUMENT) {
                    throw ApiException.badRequest("INVALID_DIGITAL_FILE",
                            "Upload the resource as a document before sharing it.");
                }
                effectiveDigitalId = asset.getId();
                if (condition != null) {
                    throw ApiException.badRequest("DIGITAL_CONFLICT",
                            "Digital resources do not have a physical condition.");
                }
            }
            case EXTERNAL_LINK -> {
                String trimmedUrl = externalUrl == null ? "" : externalUrl.trim();
                if (!trimmedUrl.startsWith("https://") || trimmedUrl.length() < 9
                        || trimmedUrl.contains(" ")) {
                    throw ApiException.badRequest("INVALID_EXTERNAL_URL",
                            "Provide a secure https:// link to the resource.");
                }
                effectiveExternalUrl = trimmedUrl;
                if (condition != null) {
                    throw ApiException.badRequest("DIGITAL_CONFLICT",
                            "Digital resources do not have a physical condition.");
                }
            }
        }

        return new ResolvedShape(category, book, effectiveTitle, effectiveCondition, medium,
                effectiveExternalUrl, effectiveDigitalId, ownerRightsConfirmed);
    }

    private void applyMetadata(Listing listing, ResolvedShape shape, String subject, String academicLevel,
                               String board, String author, String publisher, String edition,
                               Integer publicationYear) {
        listing.setCategory(shape.category());
        listing.setBook(shape.book());
        listing.setTitle(shape.title());
        listing.setSubject(blankToNull(subject));
        listing.setAcademicLevel(blankToNull(academicLevel));
        listing.setBoard(shape.book() == null ? blankToNull(board) : shape.book().getEducationBoard().name());
        listing.setAuthor(shape.book() == null ? blankToNull(author) : shape.book().getAuthor());
        listing.setPublisher(shape.book() == null ? blankToNull(publisher) : shape.book().getPublisher());
        listing.setEdition(shape.book() == null ? blankToNull(edition) : shape.book().getEdition());
        listing.setPublicationYear(publicationYear == null ? null : publicationYear.shortValue());
        listing.setCondition(shape.condition());
        listing.setMedium(shape.medium());
    }

    private void applyOfferFields(Listing listing, PriceType priceType, BigDecimal price, String locality,
                                  String notes, LocationService.ResolvedLocation location) {
        listing.setPriceType(priceType);
        listing.setPrice(normalizePrice(priceType, price));
        if (location.cityName() == null) {
            throw ApiException.badRequest("CITY_REQUIRED", "Choose where the handover happens.");
        }
        listing.setCity(location.cityName());
        listing.setLocality(trimToNull(locality));
        listing.setNotes(trimToNull(notes));
        listing.setCountryId(location.countryId());
        listing.setStateId(location.stateId());
        listing.setCityId(location.cityId());
    }

    /** A listing with no location of its own falls back to the owner's pickup area. */
    private LocationService.ResolvedLocation location(ShapedLocation shaped, User owner) {
        return locationService.resolve(shaped.countryId(), shaped.stateId(), shaped.cityId(),
                shaped.city() != null && !shaped.city().isBlank() ? shaped.city() : owner.getCity(),
                owner.getState(), owner.getCountry());
    }

    private void attachImages(Listing listing, List<UUID> imageIds, UUID userId, boolean update) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }
        if (imageIds.size() > 5) {
            throw ApiException.badRequest("TOO_MANY_IMAGES", "You can add up to 5 photos.");
        }
        Set<UUID> unique = new HashSet<>();
        short position = 0;
        for (UUID imageId : imageIds) {
            if (!unique.add(imageId)) {
                throw ApiException.badRequest("DUPLICATE_IMAGE", "Each photo can only be used once.");
            }
            MediaAsset asset = mediaService.getRequired(imageId);
            if (!asset.getOwner().getId().equals(userId)) {
                throw ApiException.forbidden("You can only use photos you uploaded.");
            }
            if (asset.getPurpose() != MediaPurpose.LISTING) {
                throw ApiException.badRequest("INVALID_IMAGE", "That image was not uploaded for a listing.");
            }
            boolean usedElsewhere = update
                    ? listingImageRepository.existsByMediaIdAndListingIdNot(imageId, listing.getId())
                    : listingImageRepository.existsByMediaId(imageId);
            if (usedElsewhere) {
                throw ApiException.conflict("IMAGE_IN_USE", "One of these photos is already attached to a listing.");
            }
            ListingImage image = new ListingImage();
            image.setMedia(asset);
            image.setPosition(position++);
            listing.addImage(image);
        }
    }

    private void assertAllowedTransition(ListingStatus from, ListingStatus to) {
        boolean allowed = switch (from) {
            case AVAILABLE -> to == ListingStatus.UNAVAILABLE || to == ListingStatus.WITHDRAWN
                    || to == ListingStatus.EXCHANGED;
            case UNAVAILABLE -> to == ListingStatus.AVAILABLE || to == ListingStatus.WITHDRAWN
                    || to == ListingStatus.EXCHANGED;
            case WITHDRAWN -> to == ListingStatus.AVAILABLE || to == ListingStatus.UNAVAILABLE;
            case EXCHANGED -> false;
        };
        if (!allowed) {
            throw ApiException.badRequest("INVALID_STATUS_TRANSITION",
                    "That status change is not allowed for this listing.");
        }
    }

    private void recordTransition(Listing listing, ListingStatus from, ListingStatus to, User actor, String source) {
        ListingStatusHistory entry = new ListingStatusHistory();
        entry.setListing(listing);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(actor);
        entry.setSource(source);
        statusHistoryRepository.save(entry);
    }

    private Listing ownedListing(UUID listingId, UUID userId) {
        return listingRepository.findWithDetailsById(listingId)
                .filter(listing -> listing.getOwner().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
    }

    private BigDecimal normalizePrice(PriceType type, BigDecimal price) {
        if (type == PriceType.FREE) {
            if (price != null && price.compareTo(BigDecimal.ZERO) != 0) {
                throw ApiException.badRequest("INVALID_PRICE", "Free listings cannot have a price.");
            }
            return null;
        }
        if (type == PriceType.FIXED_PRICE && price == null) {
            throw ApiException.badRequest("INVALID_PRICE", "Enter a price for a fixed-price listing.");
        }
        if (price != null) {
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("INVALID_PRICE", "Price must be greater than zero.");
            }
            if (price.compareTo(new BigDecimal("9999999.99")) > 0) {
                throw ApiException.badRequest("INVALID_PRICE", "Price is too large.");
            }
            return price.setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private boolean underDailyLimit(UUID userId) {
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        return listingRepository.countByOwnerIdAndCreatedAtAfter(userId, since) < MAX_DAILY_LISTINGS;
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 48));
    }

    private String fileExtension(MediaAsset asset) {
        String type = asset.getContentType().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "application/pdf" -> "pdf";
            case "text/plain" -> "txt";
            default -> "bin";
        };
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
