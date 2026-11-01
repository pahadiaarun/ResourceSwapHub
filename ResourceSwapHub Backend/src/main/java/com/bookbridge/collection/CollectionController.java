package com.bookbridge.collection;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class CollectionController {
    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/collections")
    public ResponseEntity<ApiResponse<CollectionDetailDto>> create(
            @Valid @RequestBody CollectionCreateRequest request) {
        CollectionDetailDto collection = collectionService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(collection));
    }

    @GetMapping("/collections/{id}")
    public ApiResponse<CollectionDetailDto> get(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(collectionService.get(id, SecurityUtils.optionalUserId(authentication)));
    }

    @PatchMapping("/collections/{id}")
    public ApiResponse<CollectionDetailDto> update(@PathVariable UUID id,
                                                    @Valid @RequestBody CollectionUpdateRequest request) {
        return ApiResponse.ok(collectionService.update(SecurityUtils.currentUserId(), id, request));
    }

    @GetMapping("/users/me/collections")
    public ApiResponse<PageResponse<CollectionSummaryDto>> mine(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        Page<CollectionSummaryDto> result = collectionService.mine(SecurityUtils.currentUserId(), page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
