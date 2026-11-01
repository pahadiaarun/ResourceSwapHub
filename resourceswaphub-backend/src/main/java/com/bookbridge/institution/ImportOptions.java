package com.bookbridge.institution;

/**
 * Provenance and behaviour for one import run. {@code markVerified} should only be true
 * when the dataset is an authoritative government source; "verified" here means "present
 * in an authoritative dataset", never that any user's affiliation is verified.
 */
public record ImportOptions(
        String sourceName,
        String sourceUrl,
        String datasetVersion,
        String academicYear,
        boolean markVerified
) {
    public static ImportOptions curatedSeed() {
        return new ImportOptions("CURATED_SEED", null, "seed-1", null, false);
    }
}
