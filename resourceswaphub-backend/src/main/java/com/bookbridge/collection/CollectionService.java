package com.bookbridge.collection;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.listing.Listing;
import com.bookbridge.listing.ListingDto;
import com.bookbridge.listing.ListingMapper;
import com.bookbridge.listing.ListingRepository;
import com.bookbridge.listing.ListingStatus;
import com.bookbridge.listing.SellerDto;
import com.bookbridge.location.LocationDto;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.user.User;
import com.bookbridge.user.UserService;

@Service
public class CollectionService {
    private final CollectionRepository collectionRepository;
    private final ListingRepository listingRepository;
    private final ListingMapper listingMapper;
    private final UserService userService;
    private final LocationService locationService;

    public CollectionService(CollectionRepository collectionRepository, ListingRepository listingRepository,
                            ListingMapper listingMapper, UserService userService,
                            LocationService locationService) {
        this.collectionRepository = collectionRepository;
        this.listingRepository = listingRepository;
        this.listingMapper = listingMapper;
        this.userService = userService;
        this.locationService = locationService;
    }

    @Transactional
    public CollectionDetailDto create(UUID userId, CollectionCreateRequest request) {
        User owner = userService.require(userId);
        if (!owner.isProfileComplete()) {
            throw ApiException.badRequest("PROFILE_INCOMPLETE",
                    "Add your city and locality before creating a collection.");
        }
        Collection collection = new Collection();
        collection.setOwner(owner);
        applyFields(collection, request.title(), request.description(), request.locality(),
                location(request.countryId(), request.stateId(), request.cityId(), request.city(), owner));
        collectionRepository.saveAndFlush(collection);
        return detail(collection, owner.getId());
    }

    @Transactional
    public CollectionDetailDto update(UUID userId, UUID collectionId, CollectionUpdateRequest request) {
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .orElseThrow(() -> ApiException.notFound("That collection could not be found."));
        if (!collection.getOwner().getId().equals(userId)) {
            throw ApiException.notFound("That collection could not be found.");
        }
        applyFields(collection, request.title(), request.description(), request.locality(),
                location(request.countryId(), request.stateId(), request.cityId(), request.city(),
                        collection.getOwner()));
        collectionRepository.saveAndFlush(collection);
        return detail(collection, userId);
    }

    @Transactional(readOnly = true)
    public CollectionDetailDto get(UUID collectionId, UUID viewerId) {
        Collection collection = collectionRepository.findWithOwnerById(collectionId)
                .orElseThrow(() -> ApiException.notFound("That collection could not be found."));
        return detail(collection, viewerId);
    }

    @Transactional(readOnly = true)
    public Page<CollectionSummaryDto> mine(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 48)));
        Page<Collection> result = collectionRepository.findByOwnerIdOrderByCreatedAtDesc(userId, pageable);
        LocationService.LocationIndex index = locationService.index(
                result.getContent().stream().map(Collection::getCountryId).toList(),
                result.getContent().stream().map(Collection::getStateId).toList(),
                result.getContent().stream().map(Collection::getCityId).toList());
        return result.map(collection -> summary(collection, userId, index));
    }

    /** A collection with no location of its own inherits the owner's pickup area. */
    private LocationService.ResolvedLocation location(UUID countryId, UUID stateId, UUID cityId,
                                                     String cityText, User owner) {
        return locationService.resolve(countryId, stateId, cityId,
                cityText != null && !cityText.isBlank() ? cityText : owner.getCity(),
                owner.getState(), owner.getCountry());
    }

    private CollectionDetailDto detail(Collection collection, UUID viewerId) {
        boolean owner = collection.getOwner().getId().equals(viewerId);
        List<Listing> all = listingRepository.findByCollectionIdOrderByCreatedAtDesc(collection.getId());
        List<Listing> visible = all.stream()
                .filter(listing -> owner || listing.getStatus() != ListingStatus.WITHDRAWN)
                .toList();
        List<ListingDto> items = listingMapper.toDtoList(visible);
        long available = visible.stream().filter(l -> l.getStatus() == ListingStatus.AVAILABLE).count();
        String cover = firstImage(visible);
        return new CollectionDetailDto(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getCity(),
                collection.getLocality(),
                describe(collection),
                SellerDto.from(collection.getOwner()),
                visible.size(),
                available,
                cover,
                collection.getCreatedAt(),
                items
        );
    }

    private CollectionSummaryDto summary(Collection collection, UUID viewerId,
                                        LocationService.LocationIndex index) {
        List<Listing> visible = listingRepository.findByCollectionIdOrderByCreatedAtDesc(collection.getId()).stream()
                .filter(listing -> collection.getOwner().getId().equals(viewerId)
                        || listing.getStatus() != ListingStatus.WITHDRAWN)
                .toList();
        long available = visible.stream().filter(l -> l.getStatus() == ListingStatus.AVAILABLE).count();
        return new CollectionSummaryDto(
                collection.getId(),
                collection.getTitle(),
                collection.getDescription(),
                collection.getCity(),
                collection.getLocality(),
                index.of(collection.getCountryId(), collection.getStateId(), collection.getCityId()),
                SellerDto.from(collection.getOwner()),
                visible.size(),
                available,
                firstImage(visible),
                collection.getCreatedAt()
        );
    }

    private LocationDto describe(Collection collection) {
        return locationService.describe(collection.getCountryId(), collection.getStateId(), collection.getCityId());
    }

    private String firstImage(List<Listing> listings) {
        return listings.stream()
                .flatMap(listing -> listing.getImages().stream())
                .findFirst()
                .map(image -> image.getMedia().publicUrl())
                .orElse(null);
    }

    private void applyFields(Collection collection, String title, String description, String locality,
                             LocationService.ResolvedLocation location) {
        collection.setTitle(title.trim());
        collection.setDescription(trimToNull(description));
        if (location.cityName() == null) {
            throw ApiException.badRequest("CITY_REQUIRED", "Choose the city where the handover happens.");
        }
        collection.setCity(location.cityName());
        collection.setLocality(trimToNull(locality));
        collection.setCountryId(location.countryId());
        collection.setStateId(location.stateId());
        collection.setCityId(location.cityId());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
