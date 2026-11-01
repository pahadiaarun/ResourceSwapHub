package com.bookbridge.location;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;

/**
 * Public location directory. Reads are open so unauthenticated registration and search pages can
 * populate the cascading selects; writes happen only through the operator-run import pipeline.
 */
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/countries")
    public ApiResponse<List<CountryDto>> countries(@RequestParam(name = "q", required = false) String q) {
        return ApiResponse.ok(locationService.countries(q));
    }

    @GetMapping("/states")
    public ApiResponse<List<StateDto>> states(@RequestParam(name = "countryId") UUID countryId,
                                              @RequestParam(name = "q", required = false) String q) {
        return ApiResponse.ok(locationService.states(countryId, q));
    }

    @GetMapping("/districts")
    public ApiResponse<List<DistrictDto>> districts(@RequestParam(name = "stateId") UUID stateId,
                                                    @RequestParam(name = "q", required = false) String q) {
        return ApiResponse.ok(locationService.districts(stateId, q));
    }

    @GetMapping("/cities")
    public ApiResponse<PageResponse<CityDto>> cities(
            @RequestParam(name = "stateId") UUID stateId,
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(locationService.cities(stateId, q, pageable));
    }

    @GetMapping("/cities/{id}")
    public ApiResponse<CityDto> city(@PathVariable UUID id) {
        return ApiResponse.ok(locationService.city(id));
    }
}
