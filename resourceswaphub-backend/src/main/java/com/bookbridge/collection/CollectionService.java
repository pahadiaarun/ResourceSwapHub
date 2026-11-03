package com.bookbridge.collection;

import java.util.List;
import java.util.Locale;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.category.CategoryService;
import com.bookbridge.category.ResourceCategory;
import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingDto;
import com.bookbridge.listing.ListingMapper;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.ListingStatus;
import com.bookbridge.location.LocationDto;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.media.MediaAsset;
import com.bookbridge.shared.media.MediaPurpose;
import com.bookbridge.shared.media.MediaService;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class CollectionService {
    static final int MAX_ITEMS = 100;

    private final CollectionRepository collectionRepository;
    private final CollectionItemRepository itemRepository;
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final UserService userService;
    private final LocationService locationService;
    private final CategoryService categoryService;
    private final MediaService mediaService;

    public CollectionService(CollectionRepository collectionRepository, CollectionItemRepository itemRepository,
                             ListingRepository listingRepository, ListingMapper listingMapper,
                             UserService userService, LocationService locationService,
                             CategoryService categoryService, MediaService mediaService) {
        this.collectionRepository = collectionRepository;
        this.itemRepository = itemRepository;
        this.listingRepository = listingRepository;
        this.listingMapper = listingMapper;
        this.userService = userService;
        this.locationService = locationService;
        this.categoryService = categoryService;
        this.mediaService = mediaService;
    }

    // ------------------------------------------------------------------------
    // Write paths
    // ------------------------------------------------------------------------

    @Transactional
    public CollectionDetailDto create(UUID userId, CollectionCreateRequest request) {
        User owner = userService.require(userId);
        if (!owner.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before creating a collection.");
        }
        Collection collection = new Collection();
        collection.setOwner(owner);
        applyFields(collection, request.title(), request.description(), request.categoryId(),
                request.academicLevel(), request.institution(), request.coverMediaId(), false,
                request.locality(),
                location(ShapedLocation.of(request), owner));
        if (Boolean.TRUE.equals(request.published())) {
            publish(collection);
        }
        collectionRepository.saveAndFlush(collection);
        return detail(collection, owner.getId());
    }

    @Transactional
    public CollectionDetailDto update(UUID userId, UUID collectionId, CollectionUpdateRequest request) {
        Collection collection = ownedCollection(collectionId, userId);
        applyFields(collection,
                request.title() != null ? request.title() : collection.getTitle(),
                request.description() != null ? request.description() : collection.getDescription(),
                request.categoryId(),
                request.academicLevel() != null ? request.academicLevel() : collection.getAcademicLevel(),
                request.institution() != null ? request.institution() : collection.getInstitution(),
                request.coverMediaId(),
                request.removeCover() != null && request.removeCover() && request.coverMediaId() == null,
                request.locality(),
                location(ShapedLocation.ofUpdate(collection.getOwner(), request), collection.getOwner()));
        if (request.published() != null) {
            if (request.published()) {
                publish(collection);
            } else {
                collection.setVisibility(CollectionVisibility.PRIVATE);
            }
        }
        collectionRepository.saveAndFlush(collection);
        return detail(collection, userId);
    }

    @Transactional
    public void delete(UUID userId, UUID collectionId) {
        Collection collection = ownedCollection(collectionId, userId);
        collectionRepository.delete(collection);
    }

    @Transactional
    public CollectionDetailDto addResource(UUID userId, UUID collectionId, UUID listingId) {
        Collection collection = ownedCollection(collectionId, userId);
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (!listing.getOwner().getId().equals(userId)) {
            throw ApiException.forbidden("You can only add your own resources to a collection.");
        }
        if (itemRepository.countByCollectionId(collectionId) >= MAX_ITEMS) {
            throw ApiException.badRequest("COLLECTION_FULL",
                    "A collection can hold at most " + MAX_ITEMS + " resources.");
        }
        if (itemRepository.existsByCollectionIdAndListingId(collectionId, listingId)) {
            throw ApiException.conflict("DUPLICATE_COLLECTION_ITEM",
                    "This resource is already part of the collection.");
        }
        CollectionItem item = new CollectionItem();
        item.setCollection(collection);
        item.setListing(listing);
        itemRepository.save(item);
        return detail(collection, userId);
    }

    /**
     * Attaching at listing-creation time, where the caller named the collection inside their own
     * listing payload. A foreign id is reported as forbidden rather than hidden behind a 404 the way
     * the collection-management endpoints do it, because here the caller is asserting the id as theirs.
     */
    @Transactional
    public void attachForListingCreate(UUID userId, UUID collectionId, UUID listingId) {
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .orElseThrow(() -> ApiException.badRequest("COLLECTION_NOT_FOUND",
                        "That collection could not be found."));
        if (!collection.getOwner().getId().equals(userId)) {
            throw ApiException.forbidden("You can only add resources to your own collections.");
        }
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (itemRepository.countByCollectionId(collection.getId()) >= MAX_ITEMS) {
            throw ApiException.badRequest("COLLECTION_FULL",
                    "A collection can hold at most " + MAX_ITEMS + " resources.");
        }
        if (itemRepository.existsByCollectionIdAndListingId(collection.getId(), listingId)) {
            return;
        }
        CollectionItem item = new CollectionItem();
        item.setCollection(collection);
        item.setListing(listing);
        itemRepository.save(item);
    }

    @Transactional
    public CollectionDetailDto removeResource(UUID userId, UUID collectionId, UUID listingId) {
        Collection collection = ownedCollection(collectionId, userId);
        CollectionItem item = itemRepository.findByCollectionIdAndListingId(collectionId, listingId)
                .orElseThrow(() -> ApiException.notFound("That resource is not part of this collection."));
        itemRepository.delete(item);
        itemRepository.flush();
        return detail(collection, userId);
    }

    // ------------------------------------------------------------------------
    // Read paths
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CollectionDetailDto get(UUID collectionId, UUID viewerId) {
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .orElseThrow(() -> ApiException.notFound("That collection could not be found."));
        boolean owner = collection.getOwner().getId().equals(viewerId);
        if (!owner && collection.getVisibility() != CollectionVisibility.PUBLIC) {
            throw ApiException.notFound("That collection could not be found.");
        }
        return detail(collection, viewerId);
    }

    @Transactional(readOnly = true)
    public Page<CollectionSummaryDto> mine(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<Collection> result = collectionRepository.findByOwnerIdOrderByCreatedAtDesc(userId, pageable);
        return result.map(collection -> summary(collection, userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CollectionSummaryDto> browse(String q, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 48));
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Collection> result = q == null || q.isBlank()
                ? collectionRepository.findByVisibilityOrderByCreatedAtDesc(CollectionVisibility.PUBLIC, pageable)
                : collectionRepository.searchPublic(q.trim().toLowerCase(Locale.ROOT), pageable);
        return new PageResponse<>(result.getContent().stream()
                .map(collection -> summary(collection, null)).toList(),
                safePage, safeSize, result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }

    // ------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------

    private void publish(Collection collection) {
        collection.setVisibility(CollectionVisibility.PUBLIC);
        if (collection.getPublishedAt() == null) {
            collection.setPublishedAt(java.time.Instant.now());
        }
    }

    private Collection ownedCollection(UUID collectionId, UUID userId) {
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .orElseThrow(() -> ApiException.notFound("That collection could not be found."));
        if (!collection.getOwner().getId().equals(userId)) {
            throw ApiException.notFound("That collection could not be found.");
        }
        return collection;
    }

    private void applyFields(Collection collection, String title, String description, UUID categoryId,
                             String academicLevel, String institution, UUID coverMediaId, boolean removeCover,
                             String locality, LocationService.ResolvedLocation location) {
        collection.setTitle(title.trim());
        collection.setDescription(trimToNull(description));
        ResourceCategory category = categoryId == null ? null : categoryService.requireById(categoryId);
        collection.setCategory(category);
        collection.setAcademicLevel(trimToNull(academicLevel));
        collection.setInstitution(trimToNull(institution));
        if (removeCover) {
            collection.setCoverMedia(null);
        } else if (coverMediaId != null) {
            collection.setCoverMedia(requiredOwnImage(coverMediaId, collection.getOwner().getId()));
        }
        if (location.cityName() == null) {
            throw ApiException.badRequest("CITY_REQUIRED", "Choose where the collection handover happens.");
        }
        collection.setCity(location.cityName());
        collection.setLocality(trimToNull(locality));
        collection.setCountryId(location.countryId());
        collection.setStateId(location.stateId());
        collection.setCityId(location.cityId());
    }

    private MediaAsset requiredOwnImage(UUID mediaId, UUID userId) {
        MediaAsset asset = mediaService.getRequired(mediaId);
        if (!asset.getOwner().getId().equals(userId)) {
            throw ApiException.forbidden("You can only use images you uploaded.");
        }
        if (asset.getPurpose() != MediaPurpose.LISTING) {
            throw ApiException.badRequest("INVALID_IMAGE", "That image was not uploaded for a listing.");
        }
        return asset;
    }

    /** A collection with no location of its own inherits the owner's pickup area. */
    private LocationService.ResolvedLocation location(ShapedLocation shaped, User owner) {
        return locationService.resolve(shaped.countryId(), shaped.stateId(), shaped.cityId(),
                shaped.city() != null && !shaped.city().isBlank() ? shaped.city() : owner.getCity(),
                owner.getState(), owner.getCountry());
    }

    private record ShapedLocation(UUID countryId, UUID stateId, UUID cityId, String city) {

        static ShapedLocation of(CollectionCreateRequest request) {
            return new ShapedLocation(request.countryId(), request.stateId(), request.cityId(), request.city());
        }

        static ShapedLocation ofUpdate(User owner, CollectionUpdateRequest request) {
            return new ShapedLocation(request.countryId(), request.stateId(), request.cityId(), request.city());
        }
    }

    private CollectionDetailDto detail(Collection collection, UUID viewerId) {
        boolean owner = collection.getOwner().getId().equals(viewerId);
        List<UUID> ids = itemRepository.findListingIdsByCollectionOrderByAddedAtDesc(collection.getId());
        List<Listing> all = ids.isEmpty() ? List.of() : listingRepository.findWithDetailsByIdIn(ids);
        // Keep the read-added-at order of the membership rows.
        List<Listing> ordered = ids.stream()
                .flatMap(id -> all.stream().filter(listing -> listing.getId().equals(id)))
                .toList();
        List<Listing> visible = ordered.stream()
                .filter(listing -> owner || listing.getStatus() != ListingStatus.WITHDRAWN)
                .toList();
        List<ListingDto> items = listingMapper.toDtoList(visible);
        long available = visible.stream().filter(l -> l.getStatus() == ListingStatus.AVAILABLE).count();
        String cover = collection.getCoverMedia() != null
                ? collection.getCoverMedia().publicUrl()
                : firstImage(visible);
        return new CollectionDetailDto(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getCity(),
                collection.getLocality(),
                locationService.describe(collection.getCountryId(), collection.getStateId(), collection.getCityId()),
                com.bookbridge.listing.SellerDto.from(collection.getOwner()),
                collection.getCategory() == null ? null
                        : com.bookbridge.category.ResourceCategoryDto.from(collection.getCategory()),
                collection.getAcademicLevel(),
                collection.getInstitution(),
                collection.getVisibility().name(),
                collection.getVisibility() == CollectionVisibility.PUBLIC,
                ordered.size(),
                available,
                cover,
                collection.getCreatedAt(),
                items
        );
    }

    private CollectionSummaryDto summary(Collection collection, UUID viewerId) {
        boolean owner = viewerId != null && collection.getOwner().getId().equals(viewerId);
        List<Listing> items = listingRepository.findWithDetailsByIdIn(
                itemRepository.findListingIdsByCollectionOrderByAddedAtDesc(collection.getId()));
        List<Listing> visible = items.stream()
                .filter(listing -> owner || listing.getStatus() != ListingStatus.WITHDRAWN)
                .toList();
        long available = visible.stream().filter(l -> l.getStatus() == ListingStatus.AVAILABLE).count();
        String cover = collection.getCoverMedia() != null
                ? collection.getCoverMedia().publicUrl()
                : firstImage(visible);
        return new CollectionSummaryDto(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getCity(),
                collection.getLocality(),
                locationDescribe(collection),
                com.bookbridge.listing.SellerDto.from(collection.getOwner()),
                collection.getCategory() == null ? null
                        : com.bookbridge.category.ResourceCategoryDto.from(collection.getCategory()),
                collection.getAcademicLevel(),
                collection.getInstitution(),
                collection.getVisibility().name(),
                collection.getVisibility() == CollectionVisibility.PUBLIC,
                items.size(),
                available,
                cover,
                collection.getCreatedAt()
        );
    }

    private LocationDto locationDescribe(Collection collection) {
        return locationService.describe(collection.getCountryId(), collection.getStateId(), collection.getCityId());
    }

    private String firstImage(List<Listing> listings) {
        return listings.stream()
                .flatMap(listing -> listing.getImages().stream())
                .findFirst()
                .map(image -> image.getMedia().publicUrl())
                .orElse(null);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
