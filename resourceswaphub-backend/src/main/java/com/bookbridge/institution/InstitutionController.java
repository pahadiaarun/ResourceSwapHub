package com.bookbridge.institution;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/institutions")
public class InstitutionController {
    private final InstitutionService institutionService;

    public InstitutionController(InstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<InstitutionDto>> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "city", required = false) String city,
            Pageable pageable) {
        return ApiResponse.ok(institutionService.search(q, type, state, city, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<InstitutionDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(institutionService.get(id));
    }

    @PostMapping("/suggestions")
    public ApiResponse<InstitutionSuggestion> suggest(@Valid @RequestBody InstitutionSuggestionRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.ok(institutionService.submitSuggestion(userId, request));
    }
}
