package com.bookbridge.location;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds the location directory on boot so the cascading selects are usable out of the box: the LGD
 * districts, imported verbatim from the Government of India Local Government Directory, then the
 * curated settlement list, which tops itself up on later boots when the seed file grows.
 * Idempotent: the importer upserts by (state, normalized name), and the curated run uses
 * insert-missing-only semantics — rows already stored are never rewritten, so names, official
 * codes and provenance from an authorized LGD/Census import are preserved.
 *
 * <p>LGD rows carry {@code source = LGD}, the portal URL and the dataset version; their local
 * language names become aliases. Curated rows carry {@code source = CURATED_SEED} and no
 * {@code official_code}: well-known settlement names are recorded, government identifiers never are.
 *
 * <p>Never fatal: failures are logged so startup cannot crash. Disable with
 * {@code bookbridge.location.seed=false}.
 */
@Component
@ConditionalOnProperty(prefix = "bookbridge.location", name = "seed", havingValue = "true", matchIfMissing = true)
public class LocationSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationSeedRunner.class);
    private static final String SEED_RESOURCE = "location-seed/india-curated.json";
    private static final String LGD_RESOURCE = "location-seed/india-lgd-districts.json";
    private static final String LGD_SOURCE = "LGD";
    private static final String LGD_DATASET_VERSION = "2026-11-03";

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final LocationImportService importService;

    public LocationSeedRunner(CityRepository cityRepository, DistrictRepository districtRepository,
                              LocationImportService importService) {
        this.cityRepository = cityRepository;
        this.districtRepository = districtRepository;
        this.importService = importService;
    }

    @Override
    public void run(String... args) {
        importLgdDistricts();
        try {
            long existing = cityRepository.count();
            ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
            if (!resource.exists()) {
                log.warn("Location seed skipped: resource '{}' not found", SEED_RESOURCE);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                LocationImportSummary summary = importService.importJson(in, LocationImportOptions.curatedSeed());
                log.info("Location seed top-up ({}, {} cities present before): states={} districts={} cities={} "
                                + "inserted={} skipped={} failed={}",
                        existing == 0 ? "first boot" : "existing directory", existing,
                        summary.states(), summary.districts(), summary.cities(),
                        summary.inserted(), summary.skipped(), summary.failed());
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.error("Location seeding failed (startup continues): {}", ex.getMessage(), ex);
        }
    }

    /**
     * Districts are official data, so they are imported once per dataset version rather than
     * reconciled on every boot: an official run rewrites each row it matches, and doing that for
     * 790 districts on a remote database would put minutes of writes in front of every cold start.
     * Bumping {@link #LGD_DATASET_VERSION} after regenerating the file makes the next boot refresh.
     */
    private void importLgdDistricts() {
        if (districtRepository.existsBySourceAndDatasetVersion(LGD_SOURCE, LGD_DATASET_VERSION)) {
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource(LGD_RESOURCE);
            if (!resource.exists()) {
                log.warn("LGD district import skipped: resource '{}' not found", LGD_RESOURCE);
                return;
            }
            LocationImportOptions options = new LocationImportOptions(LGD_SOURCE,
                    "https://lgdirectory.gov.in/", LGD_DATASET_VERSION, true);
            try (InputStream in = resource.getInputStream()) {
                LocationImportSummary summary = importService.importJson(in, options);
                log.info("LGD district import: states={} districts={} inserted={} updated={} conflicts={} failed={}",
                        summary.states(), summary.districts(), summary.inserted(), summary.updated(),
                        summary.conflicts(), summary.failed());
                summary.errors().forEach(error -> log.warn("  LGD import issue: {}", error));
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.error("LGD district import failed (startup continues): {}", ex.getMessage(), ex);
        }
    }
}
