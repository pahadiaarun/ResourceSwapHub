package com.bookbridge.user;

import java.time.Instant;
import java.util.UUID;

import com.bookbridge.location.LocationDto;

public record UserDto(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        String city,
        String locality,
        LocationDto location,
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
                new LocationDto(user.getCountryId(), user.getStateId(), user.getCityId(),
                        user.getCountry(), user.getState(), user.getCity()),
                user.isProfileComplete(),
                user.getCreatedAt()
        );
    }
}
