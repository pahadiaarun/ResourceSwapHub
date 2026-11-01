package com.bookbridge.institution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstitutionSuggestionRequest(
        @NotBlank @Size(max = 300) String name,
        @NotBlank @Size(max = 50) String institutionType,
        @Size(max = 80) String state,
        @Size(max = 80) String city,
        @Size(max = 80) String district,
        @Size(max = 1000) String additionalInfo
) {
}
