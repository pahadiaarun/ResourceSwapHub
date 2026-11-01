package com.bookbridge.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookCreateRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title is too long")
        String title,
        @NotBlank(message = "Author is required")
        @Size(max = 255, message = "Author is too long")
        String author,
        @Size(max = 255, message = "Publisher is too long")
        String publisher,
        @Size(max = 20, message = "ISBN is too long")
        String isbn,
        @Min(value = 1, message = "Class must be between 1 and 12")
        @Max(value = 12, message = "Class must be between 1 and 12")
        int classLevel,
        @NotBlank(message = "Education board is required")
        String board,
        @NotBlank(message = "Subject is required")
        @Size(max = 120, message = "Subject is too long")
        String subject,
        @Size(max = 80, message = "Edition is too long")
        String edition,
        @Size(max = 60, message = "Language is too long")
        String language
) {
}
