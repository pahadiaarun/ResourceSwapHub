package com.bookbridge.listing;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/listings")
public class ListingController {
    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ListingDto>> create(@Valid @RequestBody ListingCreateRequest request) {
        ListingDto listing = listingService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(listing));
    }

    @GetMapping("/{id}")
    public ApiResponse<ListingDto> get(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(listingService.getForViewer(id, SecurityUtils.optionalUserId(authentication)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ListingDto> update(@PathVariable UUID id,
                                           @Valid @RequestBody ListingUpdateRequest request) {
        return ApiResponse.ok(listingService.update(SecurityUtils.currentUserId(), id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ListingDto> updateStatus(@PathVariable UUID id,
                                                 @Valid @RequestBody ListingStatusUpdateRequest request) {
        return ApiResponse.ok(listingService.updateStatus(SecurityUtils.currentUserId(), id, request.status()));
    }
}
