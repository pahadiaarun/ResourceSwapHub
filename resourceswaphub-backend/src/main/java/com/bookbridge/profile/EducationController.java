package com.bookbridge.profile;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/profile")
public class EducationController {

    private final EducationService educationService;

    public EducationController(EducationService educationService) {
        this.educationService = educationService;
    }

    @GetMapping("/me/education")
    public ApiResponse<List<EducationDto>> mine() {
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(educationService.listFor(userId, userId));
    }

    @PostMapping("/me/education")
    public ResponseEntity<ApiResponse<EducationDto>> create(@Valid @RequestBody EducationRequest request) {
        EducationDto dto = educationService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @PatchMapping("/me/education/{id}")
    public ApiResponse<EducationDto> update(@PathVariable UUID id, @Valid @RequestBody EducationRequest request) {
        return ApiResponse.ok(educationService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/me/education/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        educationService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/education")
    public ApiResponse<List<EducationDto>> forUser(@PathVariable UUID userId) {
        return ApiResponse.ok(educationService.listFor(SecurityUtils.currentUserId(), userId));
    }
}
