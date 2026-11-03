package com.bookbridge.request;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.communication.ConversationSummaryDto;
import com.bookbridge.listing.BookCondition;
import com.bookbridge.shared.api.ApiResponse;
import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.security.SecurityUtils;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class ResourceRequestController {

    private final ResourceRequestService requestService;

    public ResourceRequestController(ResourceRequestService requestService) {
        this.requestService = requestService;
    }

    // Browse is anonymous; writes and mine/* require session.
    @GetMapping("/resource-requests")
    public ApiResponse<PageResponse<ResourceRequestDto>> browse(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "academicLevel", required = false) String academicLevel,
            @RequestParam(name = "freeOnly", required = false) Boolean freeOnly,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "status", required = false) RequestStatus status,
            @RequestParam(name = "preferredCondition", required = false) BookCondition preferredCondition,
            @RequestParam(name = "stateId", required = false) UUID stateId,
            @RequestParam(name = "cityId", required = false) UUID cityId,
            @RequestParam(name = "requesterId", required = false) UUID requesterId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "12") int size) {
        return ApiResponse.ok(requestService.browse(new ResourceRequestService.BrowseQuery(
                q, categoryId, subject, academicLevel, freeOnly, minPrice, maxPrice, status,
                preferredCondition, stateId, cityId, requesterId, page, size)));
    }

    @PostMapping("/resource-requests")
    public ResponseEntity<ApiResponse<ResourceRequestDto>> create(
            @Valid @RequestBody ResourceRequestCreateRequest request) {
        ResourceRequestDto dto = requestService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(dto));
    }

    @GetMapping("/resource-requests/{id}")
    public ApiResponse<ResourceRequestDto> get(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(requestService.get(id, SecurityUtils.optionalUserId(authentication)));
    }

    @PatchMapping("/resource-requests/{id}")
    public ApiResponse<ResourceRequestDto> update(@PathVariable UUID id,
                                                   @Valid @RequestBody ResourceRequestUpdateRequest request) {
        return ApiResponse.ok(requestService.update(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/resource-requests/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        requestService.delete(SecurityUtils.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resource-requests/{id}/cancel")
    public ApiResponse<ResourceRequestDto> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(requestService.cancel(SecurityUtils.currentUserId(), id));
    }

    @PostMapping("/resource-requests/{id}/fulfill")
    public ApiResponse<ResourceRequestDto> fulfill(@PathVariable UUID id) {
        return ApiResponse.ok(requestService.fulfill(SecurityUtils.currentUserId(), id));
    }

    /** Responds with the existing chat: creates or reopens one conversation for this request. */
    @PostMapping("/resource-requests/{id}/respond")
    public ApiResponse<ConversationSummaryDto> respond(@PathVariable UUID id) {
        return ApiResponse.ok(requestService.respond(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/users/me/resource-requests")
    public ApiResponse<PageResponse<ResourceRequestDto>> mine(
            @RequestParam(name = "status", required = false) RequestStatus status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(requestService.mine(SecurityUtils.currentUserId(), status, page, size));
    }
}
