package com.bookbridge.request;

import java.util.UUID;

import com.bookbridge.user.User;

/**
 * The person asking, in the shape a request card needs. Deliberately omits the
 * seller-only fields and the micro-location, which a request never justifies.
 */
public record RequesterDto(
        UUID id,
        String displayName,
        String avatarUrl,
        String city
) {
    public static RequesterDto from(User user) {
        return new RequesterDto(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getCity()
        );
    }
}
