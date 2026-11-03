package com.bookbridge.saved;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1")
public class SavedController {

    private final SavedService savedService;

    public SavedController(SavedService savedService) {
        this.savedService = savedService;
    }

    @GetMapping("/users/me/saved-resources")
    public ApiResponse<PageResponse<SavedResourceDto>> mine(
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "24") int size) {
        return ApiResponse.ok(savedService.myListings(SecurityUtils.currentUserId(), categoryId, page, size));
    }

    @PostMapping("/listings/{id}/save")
    public ApiResponse<Map<String, Boolean>> saveListing(@PathVariable UUID id) {
        boolean created = savedService.saveListing(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(Map.of("saved", true, "created", created));
    }

    @DeleteMapping("/listings/{id}/save")
    public ApiResponse<Map<String, Boolean>> unsaveListing(@PathVariable UUID id) {
        savedService.unsaveListing(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(Map.of("saved", false));
    }

    @GetMapping("/users/me/saved-collections")
    public ApiResponse<PageResponse<SavedCollectionDto>> myCollections(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "24") int size) {
        return ApiResponse.ok(savedService.myCollections(SecurityUtils.currentUserId(), page, size));
    }

    @PostMapping("/collections/{id}/save")
    public ApiResponse<Map<String, Boolean>> saveCollection(@PathVariable UUID id) {
        boolean created = savedService.saveCollection(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(Map.of("saved", true, "created", created));
    }

    @DeleteMapping("/collections/{id}/save")
    public ApiResponse<Map<String, Boolean>> unsaveCollection(@PathVariable UUID id) {
        savedService.unsaveCollection(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(Map.of("saved", false));
    }
}
