package com.bookbridge.institution;

import java.util.List;

/** Outcome of one import run, reported to logs and returned to callers (brief Part 7 §17). */
public record ImportSummary(
        String source,
        int total,
        int inserted,
        int updated,
        int skipped,
        int conflicts,
        int failed,
        long durationMs,
        List<String> errors
) {
}
