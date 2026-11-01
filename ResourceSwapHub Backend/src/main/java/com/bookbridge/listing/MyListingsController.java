package com.bookbridge.listing;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.security.SecurityUtils;

@RestController
@RequestMapping("/api/v1/users/me/listings")
public class MyListingsController {
    private final ListingService listingService;

    public MyListingsController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ListingDto>> mine(
            @RequestParam(name = "status", required = false) ListingStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        Page<ListingDto> result = listingService.myListings(SecurityUtils.currentUserId(), status, page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
