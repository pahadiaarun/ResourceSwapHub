package com.bookbridge.listing;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.media.MediaAsset;
import com.bookbridge.shared.media.MediaService;
import com.bookbridge.shared.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {
    private final ListingService listingService;
    private final MediaService mediaService;

    public ListingController(ListingService listingService, MediaService mediaService) {
        this.listingService = listingService;
        this.mediaService = mediaService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ListingDto>> create(@Valid @RequestBody ListingCreateRequest request) {
        ListingDto listing = listingService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(listing));
    }

    /** Bulk creation with per-entry results; valid entries publish even when others fail. */
    @PostMapping("/bulk")
    public ApiResponse<List<ListingService.BulkItemResult>> createBulk(
            @Valid @RequestBody BulkCreateRequest request) {
        return ApiResponse.ok(listingService.createBulk(SecurityUtils.currentUserId(), request.items()));
    }

    /** Authorized download for digital-file listings; moderated content is withheld. */
    @GetMapping("/{id}/file")
    public ResponseEntity<org.springframework.core.io.Resource> download(
            @PathVariable UUID id, Authentication authentication) {
        // Always requires sign-in; anonymous downloads are refused.
        UUID viewerId = SecurityUtils.currentUserId();
        ListingDto listing = listingService.getForViewer(id, viewerId);
        MediaAsset asset = listingService.digitalMediaForViewer(id, viewerId);
        FileSystemResource resource = (FileSystemResource) mediaService.load(asset);
        String filename = asset.getOriginalName() == null || asset.getOriginalName().isBlank()
                ? "resource" : asset.getOriginalName();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
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

    public record BulkCreateRequest(@Valid List<ListingCreateRequest> items) {
    }
}
