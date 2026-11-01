package com.bookbridge.institution;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Seeds a small, curated set of well-known Indian institutions on first boot so the
 * directory search is usable out of the box. Idempotent: it only runs when the
 * institutions table is empty, and the importer itself upserts by identity.
 *
 * <p>Seeded rows carry {@code source = CURATED_SEED}, {@code verification_status = PENDING}
 * and no official identifiers, because we never fabricate government codes (brief Part 7).
 * Authoritative bulk data is loaded separately through the documented import pipeline.
 *
 * <p>Never fatal: any failure is logged and swallowed so application startup cannot crash.
 * Disable with {@code bookbridge.institution.seed=false}.
 */
@Component
@ConditionalOnProperty(prefix = "bookbridge.institution", name = "seed", havingValue = "true", matchIfMissing = true)
public class InstitutionSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InstitutionSeedRunner.class);
    private static final String SEED_RESOURCE = "institution-seed/institutions.json";

    private final InstitutionRepository institutionRepository;
    private final InstitutionImportService importService;

    public InstitutionSeedRunner(InstitutionRepository institutionRepository,
                                 InstitutionImportService importService) {
        this.institutionRepository = institutionRepository;
        this.importService = importService;
    }

    @Override
    public void run(String... args) {
        try {
            if (institutionRepository.count() > 0) {
                log.debug("Institution seed skipped: {} rows already present", institutionRepository.count());
                return;
            }
            ClassPathResource resource = new ClassPathResource(SEED_RESOURCE);
            if (!resource.exists()) {
                log.warn("Institution seed skipped: resource '{}' not found", SEED_RESOURCE);
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                ImportSummary summary = importService.importJson(in, ImportOptions.curatedSeed());
                log.info("Seeded institutions: inserted={} updated={} failed={}",
                        summary.inserted(), summary.updated(), summary.failed());
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.error("Institution seeding failed (startup continues): {}", ex.getMessage(), ex);
        }
    }
}
