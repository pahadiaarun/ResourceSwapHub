package com.bookbridge.location;

/**
 * Provenance for one location import run. {@code markVerified} should only be true when the file
 * comes from an authorized official dataset that the operator has the right to use; it records
 * that the row appears in that dataset, nothing more.
 *
 * <p>{@code insertMissingOnly} marks a supplementary run (the curated seed top-up): rows already
 * in the directory are left untouched — names, official codes and provenance included — and only
 * missing rows are inserted. Official imports keep this false so a re-run refreshes stored rows
 * exactly as before.
 */
public record LocationImportOptions(
        String sourceName,
        String sourceUrl,
        String datasetVersion,
        boolean markVerified,
        boolean insertMissingOnly
) {
    /** Canonical constructor without the flag, for operator-run official imports. */
    public LocationImportOptions(String sourceName, String sourceUrl, String datasetVersion,
                                 boolean markVerified) {
        this(sourceName, sourceUrl, datasetVersion, markVerified, false);
    }

    public static LocationImportOptions curatedSeed() {
        return new LocationImportOptions("CURATED_SEED", null, null, false, true);
    }
}
