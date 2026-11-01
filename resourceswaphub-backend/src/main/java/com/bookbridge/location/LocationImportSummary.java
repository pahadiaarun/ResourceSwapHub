package com.bookbridge.location;

import java.util.List;

/** Outcome of one location import run; reported in logs and by the operator runner. */
public record LocationImportSummary(
        String source,
        int states,
        int districts,
        int cities,
        int inserted,
        int updated,
        int skipped,
        int conflicts,
        int failed,
        long durationMs,
        List<String> errors
) {
}
