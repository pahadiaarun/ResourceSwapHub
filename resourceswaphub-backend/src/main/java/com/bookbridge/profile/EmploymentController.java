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
public class EmploymentController {

    private final EmploymentService employmentService;

    public EmploymentController(EmploymentService employmentService) {
        this.employmentService = employmentService;
    }

    @GetMapping("/me/employment")
    public ApiResponse<List<EmploymentDto>> mine() {
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(employmentService.listFor(userId, userId));
    }

    @PostMapping("/me/employment")
    public ResponseEntity<ApiResponse<EmploymentDto>> create(@Valid @RequestBody EmploymentRequest request) {
        EmploymentDto dto = employmentService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @PatchMapping("/me/employment/{id}")
    public ApiResponse<EmploymentDto> update(@PathVariable UUID id, @Valid @RequestBody EmploymentRequest request) {
        return ApiResponse.ok(employmentService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/me/employment/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        employmentService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/employment")
    public ApiResponse<List<EmploymentDto>> forUser(@PathVariable UUID userId) {
        return ApiResponse.ok(employmentService.listFor(SecurityUtils.currentUserId(), userId));
    }
}
