package com.bookbridge.listing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListingCreateRequest(
        @NotNull(message = "Choose a catalogue book")
        UUID bookId,
        UUID collectionId,
        @NotNull(message = "Choose a condition")
        BookCondition condition,
        @NotNull(message = "Choose a price type")
        PriceType priceType,
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Enter a valid price")
        BigDecimal price,
        @NotBlank(message = "City is required")
        @Size(max = 80, message = "City must be 80 characters or fewer")
        String city,
        @Size(max = 120, message = "Locality must be 120 characters or fewer")
        String locality,
        @Size(max = 2000, message = "Notes must be 2000 characters or fewer")
        String notes,
        @Size(max = 5, message = "You can add up to 5 photos")
        List<UUID> imageIds
) {
}
