package com.bookbridge.listing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.bookbridge.catalog.BookDto;
import com.bookbridge.collection.CollectionRef;

@Component
public class ListingMapper {
    public ListingDto toDto(Listing listing) {
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
                listing.getNotes(),
                listing.getStatus().name(),
                images,
                listing.getCreatedAt(),
                listing.getUpdatedAt()
        );
    }
}
