package com.bookbridge.collection;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionCreateRequest(
        @NotBlank(message = "Collection name is required")
        @Size(max = 120, message = "Collection name must be 120 characters or fewer")
        String title,
        @Size(max = 1000, message = "Description must be 1000 characters or fewer")
        String description,
        UUID categoryId,
        @Size(max = 80, message = "Academic level must be 80 characters or fewer")
        String academicLevel,
        @Size(max = 200, message = "Institution must be 200 characters or fewer")
        String institution,
        UUID coverMediaId,
        @Size(max = 80, message = "City must be 80 characters or fewer")
        String city,
        @Size(max = 120, message = "Locality must be 120 characters or fewer")
        String locality,
        UUID countryId,
        UUID stateId,
        UUID cityId,
        Boolean published
) {
}
