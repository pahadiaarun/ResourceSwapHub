package com.bookbridge.location;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds the curated starter directory (districts and cities) on first boot so the cascading
 * selects are usable out of the box. Idempotent: it only runs while the cities table is empty,
 * and the importer itself upserts by (state, normalized name).
 *
 * <p>Seeded rows carry {@code source = CURATED_SEED} and no {@code official_code}: well-known
 * settlement names are recorded, government identifiers never are. Authoritative bulk data —
 * LGD district codes, Census town directories — arrives through the documented import pipeline.
 *
 * <p>Never fatal: failures are logged so startup cannot crash. Disable with
 * {@code bookbridge.location.seed=false}.
 */
@Component
@ConditionalOnProperty(prefix = "bookbridge.location", name = "seed", havingValue = "true", matchIfMissing = true)
public class LocationSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationSeedRunner.class);
    private static final String SEED_RESOURCE = "location-seed/india-curated.json";

    private final CityRepository cityRepository;
    private final LocationImportService importService;

    public LocationSeedRunner(CityRepository cityRepository, LocationImportService importService) {
        this.cityRepository = cityRepository;
        this.importService = importService;
    }

    @Override
    public void run(String... args) {
        try {
            long existing = cityRepository.count();
            if (existing > 0) {
                log.debug("Location seed skipped: {} cities already present", existing);
                return;
            }
            ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
            if (!resource.exists()) {
                log.warn("Location seed skipped: resource '{}' not found", SEED_RESOURCE);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                LocationImportSummary summary = importService.importJson(in, LocationImportOptions.curatedSeed());
                log.info("Seeded locations: states={} districts={} cities={} inserted={} failed={}",
                        summary.states(), summary.districts(), summary.cities(),
                        summary.inserted(), summary.failed());
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.error("Location seeding failed (startup continues): {}", ex.getMessage(), ex);
        }
    }
}
