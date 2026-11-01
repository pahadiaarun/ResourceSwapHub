package com.bookbridge.auth.dto;

import com.bookbridge.user.UserDto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserDto user) {
}
