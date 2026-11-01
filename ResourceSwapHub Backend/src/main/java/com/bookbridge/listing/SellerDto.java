package com.bookbridge.listing;

import java.util.UUID;

import com.bookbridge.user.User;

public record SellerDto(
        UUID id,
        String name,
        String avatarUrl,
        String city,
        String locality
) {
    public static SellerDto from(User user) {
        return new SellerDto(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getCity(),
                user.getLocality()
        );
    }
}
