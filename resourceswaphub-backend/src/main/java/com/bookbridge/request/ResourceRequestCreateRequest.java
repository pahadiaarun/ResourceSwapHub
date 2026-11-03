package com.bookbridge.request;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bookbridge.listing.BookCondition;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceRequestCreateRequest(
        @NotBlank(message = "Describe what you are looking for")
        @Size(max = 200, message = "Title must be 200 characters or fewer")
        String title,
        @Size(max = 2000, message = "Description must be 2000 characters or fewer")
        String description,
        UUID categoryId,
        @Size(max = 120, message = "Subject must be 120 characters or fewer")
        String subject,
        @Size(max = 255, message = "Author must be 255 characters or fewer")
        String author,
        @Size(max = 80, message = "Edition must be 80 characters or fewer")
        String edition,
        @Size(max = 80, message = "Academic level must be 80 characters or fewer")
        String academicLevel,
        BookCondition preferredCondition,
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Enter a valid price")
        BigDecimal priceMin,
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Enter a valid price")
        BigDecimal priceMax,
        boolean freeOnly,
        UUID countryId,
        UUID stateId,
        UUID cityId,
        @Size(max = 80, message = "City must be 80 characters or fewer")
        String city,
        @Min(value = 1, message = "Expiry must be between 1 and 90 days")
        @Max(value = 90, message = "Expiry must be between 1 and 90 days")
        Integer expiresInDays
) {
}
