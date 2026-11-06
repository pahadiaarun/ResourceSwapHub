package com.bookbridge.shared.media;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.error.ApiException;
import com.bookbridge.shared.media.MediaPurpose;
import com.bookbridge.shared.security.SecurityUtils;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaDto>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("purpose") MediaPurpose purpose) {
        MediaDto dto = mediaService.upload(SecurityUtils.currentUserId(), file, purpose);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> get(@PathVariable UUID id, Authentication authentication) {
        MediaAsset asset = mediaService.getRequired(id);
        if (asset.getPurpose() == MediaPurpose.DOCUMENT) {
            // Documents are never public: access goes through the listing file endpoint.
            UUID viewerId = SecurityUtils.optionalUserId(authentication);
            if (viewerId == null) {
                throw ApiException.unauthorized("Sign in to access this file.");
            }
            mediaService.authorizeDocumentAccess(id, viewerId);
        }
        Resource resource = mediaService.load(asset);
        CacheControl caching = asset.getPurpose() == MediaPurpose.DOCUMENT
                // Authorization decides visibility, so the response is per-viewer and must not
                // outlive it in a shared cache.
                ? CacheControl.noStore()
                : CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .cacheControl(caching)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        mediaService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
