package com.bookbridge.location;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Operator-run pass that attaches existing profiles, listings and collections to the location
 * directory. Deliberately not an HTTP endpoint and never automatic: it rewrites rows, and it only
 * makes sense once the directory has been imported.
 *
 * <p>Example: {@code java -jar app.jar --bookbridge.location.backfill=true}
 *
 * <p>It is safe to re-run. Rows that resolve stay resolved, and the ones reported as unmatched keep
 * their original text so a later run, after a bigger import, can place them.
 */
@Component
@ConditionalOnProperty(prefix = "bookbridge.location", name = "backfill", havingValue = "true")
public class LocationBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationBackfillRunner.class);

    private final LocationBackfillService backfillService;

    public LocationBackfillRunner(LocationBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            LocationBackfillSummary summary = backfillService.run();
            log.info("Location backfill complete: users={}/{} listings={}/{} collections={}/{} unmatched={} ({} ms)",
                    summary.usersMatched(), summary.usersScanned(),
                    summary.listingsMatched(), summary.listingsScanned(),
                    summary.collectionsMatched(), summary.collectionsScanned(),
                    summary.unmatched(), summary.durationMs());
            summary.unmatchedSamples().forEach(sample -> log.warn("  not standardized: {}", sample));
        } catch (Exception ex) {
            log.error("Location backfill failed: {}", ex.getMessage(), ex);
        }
    }
}
