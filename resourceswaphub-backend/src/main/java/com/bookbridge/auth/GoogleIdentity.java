package com.bookbridge.auth;

public record GoogleIdentity(
        String subject,
        String email,
        String name,
        String pictureUrl
) {
}
