package com.bookbridge.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserDto> me() {
        return ApiResponse.ok(userService.getProfile(SecurityUtils.currentUserId()));
    }

    @PatchMapping("/me")
    public ApiResponse<UserDto> update(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.currentUserId(), request));
    }
}
