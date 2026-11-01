package com.bookbridge.location;

/**
 * Provenance for one location import run. {@code markVerified} should only be true when the file
 * comes from an authorized official dataset that the operator has the right to use; it records
 * that the row appears in that dataset, nothing more.
 */
public record LocationImportOptions(
        String sourceName,
        String sourceUrl,
        String datasetVersion,
        boolean markVerified
) {
    public static LocationImportOptions curatedSeed() {
        return new LocationImportOptions("CURATED_SEED", null, null, false);
    }
}
