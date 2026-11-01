package com.bookbridge.knowledge;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bookbridge.shared.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping("/search")
    public ApiResponse<List<TopicDto>> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ApiResponse.ok(topicService.search(q, type, limit));
    }

    @PostMapping("/suggestions")
    public ApiResponse<TopicDto> suggest(@Valid @RequestBody TopicSuggestionRequest request) {
        return ApiResponse.ok(topicService.suggest(request));
    }
}
