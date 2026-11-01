package com.bookbridge.discovery;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.catalog.TaxonomyDto;
import com.bookbridge.catalog.TaxonomyService;
import com.bookbridge.listing.BookCondition;
import com.bookbridge.listing.ListingDto;
import com.bookbridge.listing.PriceType;
import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;

@RestController
@RequestMapping("/api/v1/discovery")
public class DiscoveryController {
    private final DiscoveryService discoveryService;
    private final TaxonomyService taxonomyService;

    public DiscoveryController(DiscoveryService discoveryService, TaxonomyService taxonomyService) {
        this.discoveryService = discoveryService;
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/listings")
    public ApiResponse<PageResponse<ListingDto>> listings(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "classLevel", required = false) Integer classLevel,
            @RequestParam(name = "board", required = false) String board,
            @RequestParam(name = "priceType", required = false) PriceType priceType,
            @RequestParam(name = "condition", required = false) BookCondition condition,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "stateId", required = false) UUID stateId,
            @RequestParam(name = "cityId", required = false) UUID cityId,
            @RequestParam(name = "districtId", required = false) UUID districtId,
            @RequestParam(name = "locality", required = false) String locality,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        Page<ListingDto> result = discoveryService.search(new DiscoveryService.DiscoveryQuery(
                q, subject, classLevel, board, priceType, condition, city, stateId, cityId, districtId,
                locality, page, size));
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/filters")
    public ApiResponse<TaxonomyDto> filters() {
        return ApiResponse.ok(taxonomyService.get());
    }
}
