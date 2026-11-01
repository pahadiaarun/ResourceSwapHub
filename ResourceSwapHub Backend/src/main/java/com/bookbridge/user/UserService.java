package com.bookbridge.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.media.MediaAsset;
import com.bookbridge.shared.media.MediaPurpose;
import com.bookbridge.shared.media.MediaService;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final MediaService mediaService;

    public UserService(UserRepository userRepository, MediaService mediaService) {
        this.userRepository = userRepository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public UserDto getProfile(UUID userId) {
        return UserDto.from(require(userId));
    }

    @Transactional
    public UserDto updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = require(userId);
        user.setDisplayName(request.name().trim());
        user.setCity(trimToNull(request.city()));
        user.setLocality(trimToNull(request.locality()));
        if (request.avatarMediaId() != null) {
            MediaAsset asset = mediaService.getRequired(request.avatarMediaId());
            if (!asset.getOwner().getId().equals(userId)) {
                throw ApiException.forbidden("You can only use images you uploaded.");
            }
            if (asset.getPurpose() != MediaPurpose.AVATAR) {
                throw ApiException.badRequest("INVALID_AVATAR", "That image was not uploaded as a profile picture.");
            }
            user.setAvatarUrl(asset.publicUrl());
        }
        return UserDto.from(user);
    }

    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("That user could not be found."));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
