package com.bookbridge.listing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.bookbridge.catalog.BookDto;
import com.bookbridge.collection.CollectionRef;
import com.bookbridge.location.LocationService;

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
        CollectionRef collection = listing.getCollection() == null
                ? null
                : new CollectionRef(listing.getCollection().getId(), listing.getCollection().getTitle());
        return new ListingDto(
                listing.getId(),
                BookDto.from(listing.getBook()),
                SellerDto.from(listing.getOwner()),
                collection,
                listing.getCondition().name(),
                listing.getCondition().label(),
                listing.getPriceType().name(),
                listing.getPriceType().label(),
                listing.getPrice(),
                listing.getCity(),
                listing.getLocality(),
                index.of(listing.getCountryId(), listing.getStateId(), listing.getCityId()),
                listing.getNotes(),
                listing.getStatus().name(),
                images,
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }
}
