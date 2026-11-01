package com.bookbridge.listing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.catalog.Book;
import com.bookbridge.catalog.BookRepository;
import com.bookbridge.collection.Collection;
import com.bookbridge.collection.CollectionRepository;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.media.MediaAsset;
import com.bookbridge.shared.media.MediaPurpose;
import com.bookbridge.shared.media.MediaService;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class ListingService {
    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final BookRepository bookRepository;
    private final CollectionRepository collectionRepository;
    private final MediaService mediaService;
    private final UserService userService;
    private final LocationService locationService;
    private final ListingMapper mapper;

    public ListingService(ListingRepository listingRepository, ListingImageRepository listingImageRepository,
                          BookRepository bookRepository, CollectionRepository collectionRepository,
                          MediaService mediaService, UserService userService, LocationService locationService,
                          ListingMapper mapper) {
        this.listingRepository = listingRepository;
        this.listingImageRepository = listingImageRepository;
        this.bookRepository = bookRepository;
        this.collectionRepository = collectionRepository;
        this.mediaService = mediaService;
        this.userService = userService;
        this.locationService = locationService;
        this.mapper = mapper;
    }

    @Transactional
    public ListingDto create(UUID userId, ListingCreateRequest request) {
        User owner = userService.require(userId);
        if (!owner.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before listing a book.");
        }
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> ApiException.badRequest("BOOK_NOT_FOUND", "Choose a catalogue book."));
        Collection collection = resolveCollection(request.collectionId(), userId);

        Listing listing = new Listing();
        listing.setOwner(owner);
        listing.setBook(book);
        listing.setCollection(collection);
        applyFields(listing, request.condition(), request.priceType(), request.price(),
                request.locality(), request.notes(), location(request.countryId(), request.stateId(),
                        request.cityId(), request.city(), owner));
        attachImages(listing, request.imageIds(), userId, false);
        listing.setStatus(ListingStatus.AVAILABLE);
        listingRepository.saveAndFlush(listing);
        return mapper.toDto(listing);
    }

    @Transactional
    public ListingDto update(UUID userId, UUID listingId, ListingUpdateRequest request) {
        Listing listing = ownedListing(listingId, userId);
        applyFields(listing, request.condition(), request.priceType(), request.price(),
                request.locality(), request.notes(), location(request.countryId(), request.stateId(),
                        request.cityId(), request.city(), listing.getOwner()));
        if (request.imageIds() != null) {
            listing.getImages().clear();
            listingImageRepository.flush();
            attachImages(listing, request.imageIds(), userId, true);
        }
        listingRepository.saveAndFlush(listing);
        return mapper.toDto(listing);
    }

    @Transactional
    public ListingDto updateStatus(UUID userId, UUID listingId, ListingStatus target) {
        Listing listing = ownedListing(listingId, userId);
        if (listing.getStatus() != target) {
            assertTransition(listing.getStatus(), target);
            listing.setStatus(target);
            listingRepository.saveAndFlush(listing);
        }
        return mapper.toDto(listing);
    }

    @Transactional(readOnly = true)
    public ListingDto getForViewer(UUID listingId, UUID viewerId) {
        Listing listing = listingRepository.findWithDetailsById(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        boolean owner = listing.getOwner().getId().equals(viewerId);
        if (!owner && listing.getStatus() == ListingStatus.WITHDRAWN) {
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

    @Transactional(readOnly = true)
    public List<ListingDto> forCollection(UUID collectionId, UUID viewerId) {
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .orElseThrow(() -> ApiException.notFound("That collection could not be found."));
        boolean owner = collection.getOwner().getId().equals(viewerId);
        return mapper.toDtoList(listingRepository.findByCollectionIdOrderByCreatedAtDesc(collectionId).stream()
                .filter(listing -> owner || listing.getStatus() != ListingStatus.WITHDRAWN)
                .toList());
    }

    @Transactional
    public Listing requireAvailableForConversation(UUID listingId, UUID requesterId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
        if (listing.getOwner().getId().equals(requesterId)) {
            throw ApiException.badRequest("OWN_LISTING", "This is your own listing.");
        }
        if (listing.getStatus() != ListingStatus.AVAILABLE) {
            throw ApiException.badRequest("LISTING_UNAVAILABLE", "This book is no longer available.");
        }
        return listing;
    }

    private Listing ownedListing(UUID listingId, UUID userId) {
        return listingRepository.findWithDetailsById(listingId)
                .filter(listing -> listing.getOwner().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("That listing could not be found."));
    }

    private Collection resolveCollection(UUID collectionId, UUID userId) {
        if (collectionId == null) {
            return null;
        }
        Collection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> ApiException.badRequest("COLLECTION_NOT_FOUND", "That collection could not be found."));
        if (!collection.getOwner().getId().equals(userId)) {
            throw ApiException.forbidden("You can only add books to your own collections.");
        }
        return collection;
    }

    private LocationService.ResolvedLocation location(UUID countryId, UUID stateId, UUID cityId,
                                                      String cityText, User owner) {
        // A listing with no location of its own falls back to the seller's pickup area.
        return locationService.resolve(countryId, stateId, cityId,
                cityText != null && !cityText.isBlank() ? cityText : owner.getCity(),
                owner.getState(), owner.getCountry());
    }

    private void applyFields(Listing listing, BookCondition condition, PriceType priceType,
                             BigDecimal price, String locality, String notes,
                             LocationService.ResolvedLocation location) {
        listing.setCondition(condition);
        listing.setPriceType(priceType);
        listing.setPrice(normalizePrice(priceType, price));
        if (location.cityName() == null) {
            throw ApiException.badRequest("CITY_REQUIRED", "Choose the city where the handover happens.");
        }
        listing.setCity(location.cityName());
        listing.setLocality(trimToNull(locality));
        listing.setNotes(trimToNull(notes));
        listing.setCountryId(location.countryId());
        listing.setStateId(location.stateId());
        listing.setCityId(location.cityId());
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

    private void assertTransition(ListingStatus from, ListingStatus to) {
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

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 48));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
