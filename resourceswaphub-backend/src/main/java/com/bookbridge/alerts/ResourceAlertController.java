package com.bookbridge.alerts;

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
@RequestMapping("/api/v1/users/me/resource-alerts")
public class ResourceAlertController {

    private final AlertService alertService;

    public ResourceAlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ApiResponse<List<ResourceAlertDto>> mine() {
        return ApiResponse.ok(alertService.mine(SecurityUtils.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ResourceAlertDto>> create(
            @Valid @RequestBody ResourceAlertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(alertService.create(SecurityUtils.currentUserId(), request)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ResourceAlertDto> update(@PathVariable UUID id,
                                                 @Valid @RequestBody ResourceAlertRequest request) {
        return ApiResponse.ok(alertService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        alertService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
