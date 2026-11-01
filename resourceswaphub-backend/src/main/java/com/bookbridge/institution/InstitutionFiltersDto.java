package com.bookbridge.institution;

import java.util.List;

/** Distinct facet values powering the institution search filters (brief Part 11 §28). */
public record InstitutionFiltersDto(List<String> states, List<String> types) {
}
