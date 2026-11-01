package com.bookbridge.catalog;

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
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final BookService bookService;
    private final TaxonomyService taxonomyService;

    public CatalogController(BookService bookService, TaxonomyService taxonomyService) {
        this.bookService = bookService;
        this.taxonomyService = taxonomyService;
    }

    @GetMapping("/books")
    public ApiResponse<List<BookDto>> search(@RequestParam(name = "q", defaultValue = "") String query,
                                             @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ApiResponse.ok(bookService.search(query, limit));
    }

    @PostMapping("/books")
    public ApiResponse<BookDto> create(@Valid @RequestBody BookCreateRequest request) {
        return ApiResponse.ok(bookService.findOrCreate(request));
    }

    @GetMapping("/taxonomy")
    public ApiResponse<TaxonomyDto> taxonomy() {
        return ApiResponse.ok(taxonomyService.get());
    }
}
