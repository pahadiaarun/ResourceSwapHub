package com.bookbridge.location;

import java.util.List;

/**
 * What a backfill pass did, per record type. {@code unmatched} rows keep their original free text
 * and a null location id, so they can be re-run after the directory grows.
 */
public record LocationBackfillSummary(
        int usersScanned,
        int usersMatched,
        int listingsScanned,
        int listingsMatched,
        int collectionsScanned,
        int collectionsMatched,
        int unmatched,
        List<String> unmatchedSamples,
        long durationMs
) {
}
