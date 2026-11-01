package com.bookbridge.institution;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Operator-run importer for authoritative datasets (brief Part 7 §17). It is deliberately
 * NOT an HTTP endpoint: ingesting government data is a privileged, offline operation run by
 * someone with host access, so it stays out of reach of unauthenticated callers.
 *
 * <p>Run once with the dataset path and provenance, e.g.
 * <pre>
 * java -jar app.jar \
 *   --bookbridge.institution.import-file=/data/aishe-2022.csv \
 *   --bookbridge.institution.import-source=AISHE \
 *   --bookbridge.institution.import-source-url=https://aishe.gov.in \
 *   --bookbridge.institution.import-dataset-version=2021-22 \
 *   --bookbridge.institution.import-verified=true
 * </pre>
 * The import is idempotent (upsert by official code → name+state → name) so re-runs are safe.
 * Set {@code import-verified=true} only for authoritative government sources; it records that
 * the row appears in that dataset, never that any user's affiliation is verified.
 */
@Component
@ConditionalOnProperty(prefix = "bookbridge.institution", name = "import-file")
public class InstitutionFileImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InstitutionFileImportRunner.class);

    private final InstitutionImportService importService;

    public InstitutionFileImportRunner(InstitutionImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String file = option(args, "bookbridge.institution.import-file");
        if (file == null || file.isBlank()) {
            return;
        }
        Path path = Path.of(file.trim());
        if (!Files.exists(path)) {
            log.error("Institution import aborted: file not found at {}", path);
            return;
        }
        String source = firstNonBlank(option(args, "bookbridge.institution.import-source"), "OPERATOR_IMPORT");
        ImportOptions options = new ImportOptions(
                source,
                option(args, "bookbridge.institution.import-source-url"),
                option(args, "bookbridge.institution.import-dataset-version"),
                option(args, "bookbridge.institution.import-academic-year"),
                Boolean.parseBoolean(firstNonBlank(option(args, "bookbridge.institution.import-verified"), "false")));
        try {
            ImportSummary summary = importService.importFile(path, options);
            log.info("Institution import complete [{}]: total={} inserted={} updated={} skipped={} conflicts={} failed={}",
                    summary.source(), summary.total(), summary.inserted(), summary.updated(),
                    summary.skipped(), summary.conflicts(), summary.failed());
            if (!summary.errors().isEmpty()) {
                summary.errors().forEach(e -> log.warn("  import issue: {}", e));
            }
        } catch (Exception ex) {
            log.error("Institution import failed for {}: {}", path, ex.getMessage(), ex);
        }
    }

    private static String option(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
