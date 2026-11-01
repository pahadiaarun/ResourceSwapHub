package com.bookbridge.location;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Operator-run importer for authoritative location datasets (LGD, Census town directories).
 * Deliberately not an HTTP endpoint: ingesting government data is a privileged offline operation,
 * and the operator is the party that holds the download and the right to use it.
 *
 * <p>Example:
 * <pre>
 * java -jar app.jar \
 *   --bookbridge.location.import-file=/data/lgd-districts.csv \
 *   --bookbridge.location.import-source=LGD \
 *   --bookbridge.location.import-source-url=https://lgdirectory.gov.in/ \
 *   --bookbridge.location.import-dataset-version=2024-03
 * </pre>
 *
 * <p>The import is idempotent (official code, then parent scope + normalized name), so re-runs
 * refresh the directory without creating duplicates. Nothing is invented: a row with no official
 * code is stored with {@code official_code} left null.
 */
@Component
@ConditionalOnProperty(prefix = "bookbridge.location", name = "import-file")
public class LocationFileImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocationFileImportRunner.class);

    private final LocationImportService importService;

    public LocationFileImportRunner(LocationImportService importService) {
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) {
        String file = option(args, "bookbridge.location.import-file");
        if (file == null || file.isBlank()) {
            return;
        }
        Path path = Path.of(file.trim());
        if (!Files.exists(path)) {
            log.error("Location import aborted: file not found at {}", path);
            return;
        }
        LocationImportOptions options = new LocationImportOptions(
                firstNonBlank(option(args, "bookbridge.location.import-source"), "OPERATOR_IMPORT"),
                option(args, "bookbridge.location.import-source-url"),
                option(args, "bookbridge.location.import-dataset-version"),
                Boolean.parseBoolean(firstNonBlank(option(args, "bookbridge.location.import-verified"), "false")));
        try {
            LocationImportSummary summary = importService.importFile(path, options);
            log.info("Location import complete [{}]: states={} districts={} cities={} "
                            + "inserted={} updated={} skipped={} conflicts={} failed={}",
                    summary.source(), summary.states(), summary.districts(), summary.cities(),
                    summary.inserted(), summary.updated(), summary.skipped(), summary.conflicts(),
                    summary.failed());
            summary.errors().forEach(error -> log.warn("  import issue: {}", error));
        } catch (Exception ex) {
            log.error("Location import failed for {}: {}", path, ex.getMessage(), ex);
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
