package com.bookbridge.exchange;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1")
public class ExchangeController {

    private final ExchangeService exchangeService;

    public ExchangeController(ExchangeService exchangeService) {
        this.exchangeService = exchangeService;
    }

    @GetMapping("/users/me/exchanges")
    public ApiResponse<PageResponse<ExchangeDto>> mine(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(exchangeService.mine(SecurityUtils.currentUserId(), page, size));
    }

    /** Explicit two-party confirmation that an exchange took place. */
    @PostMapping("/listings/{id}/exchange-confirmations")
    public ApiResponse<ExchangeDto> confirm(@PathVariable UUID id,
                                             @Valid @RequestBody(required = false) ConfirmRequest request) {
        UUID conversationId = request == null ? null : request.conversationId();
        return ApiResponse.ok(exchangeService.confirm(SecurityUtils.currentUserId(), id, conversationId));
    }

    @GetMapping("/listings/{id}/exchange-confirmation")
    public ApiResponse<ExchangeDto> get(@PathVariable UUID id) {
        return ApiResponse.ok(exchangeService.forListing(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/listings/{id}/exchange-confirmations")
    public ApiResponse<Map<String, String>> cancel(@PathVariable UUID id) {
        exchangeService.cancel(SecurityUtils.currentUserId(), id);
        return ApiResponse.ok(Map.of("status", "CANCELLED"));
    }

    public record ConfirmRequest(UUID conversationId) {
    }
}
