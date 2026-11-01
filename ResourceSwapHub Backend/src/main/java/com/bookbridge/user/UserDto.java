package com.bookbridge.user;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        String city,
        String locality,
        boolean profileComplete,
        Instant joinedAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCity(),
                user.getLocality(),
                user.isProfileComplete(),
                user.getCreatedAt()
        );
    }
}
