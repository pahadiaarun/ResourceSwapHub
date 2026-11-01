package com.bookbridge.communication;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/conversations")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationSummaryDto>> start(
            @Valid @RequestBody StartConversationRequest request) {
        ConversationSummaryDto conversation = chatService.start(SecurityUtils.currentUserId(), request.listingId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(conversation));
    }

    @GetMapping
    public ApiResponse<PageResponse<ConversationSummaryDto>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<ConversationSummaryDto> result = chatService.list(SecurityUtils.currentUserId(), page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountDto> unreadCount() {
        return ApiResponse.ok(chatService.unreadTotal(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationSummaryDto> get(@PathVariable java.util.UUID id) {
        return ApiResponse.ok(chatService.get(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<MessagePageDto> messages(
            @PathVariable java.util.UUID id,
            @RequestParam(name = "beforeId", required = false) Long beforeId,
            @RequestParam(name = "size", defaultValue = "30") int size) {
        return ApiResponse.ok(chatService.messages(SecurityUtils.currentUserId(), id, beforeId, size));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<MessageDto>> send(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        MessageDto message = chatService.send(SecurityUtils.currentUserId(), id, request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(message));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<UnreadCountDto> markRead(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody MarkReadRequest request) {
        return ApiResponse.ok(chatService.markRead(SecurityUtils.currentUserId(), id, request.lastMessageId()));
    }
}
