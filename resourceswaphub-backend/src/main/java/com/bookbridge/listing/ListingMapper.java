package com.bookbridge.listing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.bookbridge.catalog.BookDto;
import com.bookbridge.category.ResourceCategoryDto;
import com.bookbridge.location.LocationService;
import com.bookbridge.shared.media.MediaDto;

@Component
public class ListingMapper {
    private final LocationService locationService;

    public ListingMapper(LocationService locationService) {
        this.locationService = locationService;
    }

    public ListingDto toDto(Listing listing) {
        return toDto(listing, indexFor(List.of(listing)));
    }

    public List<ListingDto> toDtoList(List<Listing> listings) {
        LocationService.LocationIndex index = indexFor(listings);
        return listings.stream().map(listing -> toDto(listing, index)).toList();
    }

    /** One directory read for a whole page instead of three per listing. */
    public LocationService.LocationIndex indexFor(List<Listing> listings) {
        return locationService.index(
                listings.stream().map(Listing::getCountryId).toList(),
                listings.stream().map(Listing::getStateId).toList(),
                listings.stream().map(Listing::getCityId).toList());
    }

    public ListingDto toDto(Listing listing, LocationService.LocationIndex index) {
        List<String> images = listing.getImages().stream()
                .map(image -> image.getMedia().publicUrl())
                .toList();
        return new ListingDto(
                listing.getId(),
                listing.getCategory() == null ? null : ResourceCategoryDto.from(listing.getCategory()),
                listing.getBook() == null ? null : BookDto.from(listing.getBook()),
                SellerDto.from(listing.getOwner()),
                listing.getTitle(),
                listing.getCondition() == null ? null : listing.getCondition().name(),
                listing.getCondition() == null ? null : listing.getCondition().label(),
                listing.getMedium() == null ? ListingMedium.PHYSICAL.name() : listing.getMedium().name(),
                listing.getPriceType().name(),
                listing.getPriceType().label(),
                listing.getPrice(),
                listing.getCity(),
                listing.getLocality(),
                index.of(listing.getCountryId(), listing.getStateId(), listing.getCityId()),
                listing.getSubject(),
                listing.getAcademicLevel(),
                listing.getBoard(),
                listing.getAuthor(),
                listing.getPublisher(),
                listing.getEdition(),
                listing.getPublicationYear() == null ? null : listing.getPublicationYear().intValue(),
                listing.getNotes(),
                listing.getExternalUrl(),
                listing.getDigitalMedia() == null ? null : MediaDto.from(listing.getDigitalMedia()),
                listing.isOwnerRightsConfirmed(),
                listing.getStatus().name(),
                images,
                listing.getClientRef(),
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }
}
