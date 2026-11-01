package com.bookbridge.user;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 80, message = "Name must be between 2 and 80 characters")
        String name,
        @Size(max = 80, message = "City must be 80 characters or fewer")
        String city,
        @Size(max = 120, message = "Locality must be 120 characters or fewer")
        String locality,
        UUID avatarMediaId
) {
}
