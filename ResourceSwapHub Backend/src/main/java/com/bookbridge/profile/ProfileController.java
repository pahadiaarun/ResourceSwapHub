package com.bookbridge.profile;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {
    private final ExtendedProfileService profileService;

    public ProfileController(ExtendedProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ApiResponse<ExtendedProfileDto> me() {
        return ApiResponse.ok(profileService.getProfile(SecurityUtils.currentUserId()));
    }

    @PutMapping("/me")
    public ApiResponse<ExtendedProfileDto> update(@Valid @RequestBody ExtendedProfileUpdateRequest request) {
        return ApiResponse.ok(profileService.updateProfile(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{userId}")
    public ApiResponse<ExtendedProfileDto> get(@PathVariable UUID userId) {
        // Respect privacy - basic implementation
        return ApiResponse.ok(profileService.getProfile(userId));
    }
}
