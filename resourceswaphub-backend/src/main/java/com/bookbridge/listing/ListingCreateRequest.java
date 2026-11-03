package com.bookbridge.listing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Category-aware listing payload. Catalogue-anchored book listings keep sending
 * {@code bookId}; free-form resources (notes, papers, stationery…) send
 * {@code categoryId} plus their own title and metadata.
 */
public record ListingCreateRequest(
        UUID bookId,
        UUID categoryId,
        UUID collectionId,
        @Size(max = 255, message = "Title must be 255 characters or fewer")
        String title,
        ListingMedium medium,
        @Size(max = 120, message = "Subject must be 120 characters or fewer")
        String subject,
        @Size(max = 80, message = "Academic level must be 80 characters or fewer")
        String academicLevel,
        @Size(max = 20, message = "Board must be 20 characters or fewer")
        String board,
        @Size(max = 255, message = "Author must be 255 characters or fewer")
        String author,
        @Size(max = 255, message = "Publisher must be 255 characters or fewer")
        String publisher,
        @Size(max = 80, message = "Edition must be 80 characters or fewer")
        String edition,
        @Min(value = 1900, message = "Publication year looks invalid")
        @Max(value = 2100, message = "Publication year looks invalid")
        Integer publicationYear,
        BookCondition condition,
        @NotNull(message = "Choose a price type")
        PriceType priceType,
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Enter a valid price")
        BigDecimal price,
        @Size(max = 80, message = "City must be 80 characters or fewer")
        String city,
        @Size(max = 120, message = "Locality must be 120 characters or fewer")
        String locality,
        UUID countryId,
        UUID stateId,
        UUID cityId,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String notes,
        @Pattern(regexp = "^https://\\S+$", message = "External links must be a secure https:// URL")
        @Size(max = 2048, message = "External link is too long")
        String externalUrl,
        UUID digitalMediaId,
        Boolean ownerRightsConfirmed,
        @Size(max = 64, message = "Client reference must be 64 characters or fewer")
        String clientRef,
        @Size(max = 5, message = "You can add up to 5 photos")
        List<UUID> imageIds
) {
}
