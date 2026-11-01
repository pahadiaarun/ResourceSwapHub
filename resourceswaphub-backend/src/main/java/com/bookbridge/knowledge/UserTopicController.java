package com.bookbridge.knowledge;

import java.util.List;
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
public class UserTopicController {

    private final UserTopicService userTopicService;

    public UserTopicController(UserTopicService userTopicService) {
        this.userTopicService = userTopicService;
    }

    @GetMapping("/me/topics")
    public ApiResponse<List<UserTopicDto>> mine() {
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(userTopicService.listFor(userId, userId));
    }

    @PutMapping("/me/topics")
    public ApiResponse<List<UserTopicDto>> replace(@Valid @RequestBody UserTopicsUpdateRequest request) {
        return ApiResponse.ok(userTopicService.replace(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{userId}/topics")
    public ApiResponse<List<UserTopicDto>> forUser(@PathVariable UUID userId) {
        return ApiResponse.ok(userTopicService.listFor(SecurityUtils.currentUserId(), userId));
    }
}
