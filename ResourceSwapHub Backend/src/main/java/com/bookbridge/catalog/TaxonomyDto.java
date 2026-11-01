package com.bookbridge.catalog;

import java.util.List;

public record TaxonomyDto(
        List<Option> boards,
        List<Option> conditions,
        List<Option> priceTypes,
        List<Integer> classes,
        List<String> subjects,
        List<String> languages,
        List<String> cities
) {
    public record Option(String value, String label) {
    }
}
