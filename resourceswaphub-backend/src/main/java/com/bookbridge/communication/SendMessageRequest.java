package com.bookbridge.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Write a message")
        @Size(max = 4000, message = "Messages must be 4000 characters or fewer")
        String body
) {
}
