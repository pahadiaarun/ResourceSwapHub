package com.bookbridge.institution;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reusable, idempotent institution ingestion pipeline (brief Part 7 §17).
 *
 * Matching strategy, in order: official identifier → normalized name + state →
 * normalized name. Records are upserted so canonical IDs are preserved across
 * re-imports; a conflicting official code is never silently overwritten (it is
 * reported as a conflict). Each record commits in its own transaction so a single
 * bad row does not abort the whole import.
 *
 * Supported formats: JSON and CSV. XLSX should be converted to CSV before import
 * (no spreadsheet dependency is added to the build).
 */
@Service
public class InstitutionImportService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionImportService.class);
    private static final Set<String> INSTITUTION_TYPES = Set.of(
            "SCHOOL", "COLLEGE", "UNIVERSITY", "AUTONOMOUS_COLLEGE",
            "AFFILIATED_COLLEGE", "CONSTITUENT_COLLEGE", "STANDALONE", "OTHER");
    private static final Set<String> ALIAS_TYPES = Set.of("ABBREVIATION", "FORMER", "ALTERNATE", "COMMON");
    private static final int MAX_REPORTED_ERRORS = 50;

    private final InstitutionRepository institutionRepository;
    private final InstitutionAliasRepository aliasRepository;
    private final TransactionTemplate txTemplate;
    private final JsonMapper json = JsonMapper.builder().build();

    public InstitutionImportService(InstitutionRepository institutionRepository,
                                    InstitutionAliasRepository aliasRepository,
                                    PlatformTransactionManager transactionManager) {
        this.institutionRepository = institutionRepository;
        this.aliasRepository = aliasRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public ImportSummary importFile(Path file, ImportOptions options) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        try (InputStream in = Files.newInputStream(file)) {
            if (name.endsWith(".json")) {
                return importJson(in, options);
            }
            if (name.endsWith(".csv")) {
                return importCsv(in, options);
            }
            throw new IllegalArgumentException("Unsupported file type (use .json or .csv): " + file);
        }
    }

    public ImportSummary importJson(InputStream in, ImportOptions options) throws IOException {
        JsonNode root = json.readTree(in);
        List<RawInstitution> records = new ArrayList<>();
        if (root != null && root.isArray()) {
            for (JsonNode node : root) {
                records.add(fromJson(node));
            }
        }
        return upsertAll(records, options);
    }

    public ImportSummary importCsv(InputStream in, ImportOptions options) throws IOException {
        return upsertAll(parseCsv(in), options);
    }

    private ImportSummary upsertAll(List<RawInstitution> records, ImportOptions options) {
        long started = System.currentTimeMillis();
        int inserted = 0, updated = 0, skipped = 0, conflicts = 0, failed = 0;
        List<String> errors = new ArrayList<>();
        for (RawInstitution record : records) {
            try {
                Outcome outcome = txTemplate.execute(status -> upsertOne(record, options));
                switch (outcome == null ? Outcome.SKIPPED : outcome) {
                    case INSERTED -> inserted++;
                    case UPDATED -> updated++;
                    case CONFLICT -> {
                        conflicts++;
                        if (errors.size() < MAX_REPORTED_ERRORS) {
                            errors.add("conflict: " + record.name() + " (differing official code kept)");
                        }
                    }
                    case SKIPPED -> skipped++;
                }
            } catch (RuntimeException ex) {
                failed++;
                if (errors.size() < MAX_REPORTED_ERRORS) {
                    errors.add(record.name() + ": " + ex.getMessage());
                }
                log.warn("Institution import failed for '{}': {}", record.name(), ex.getMessage());
            }
        }
        ImportSummary summary = new ImportSummary(
                options.sourceName(), records.size(), inserted, updated, skipped, conflicts, failed,
                System.currentTimeMillis() - started, errors);
        log.info("Institution import [{}]: total={} inserted={} updated={} skipped={} conflicts={} failed={} ({} ms)",
                summary.source(), summary.total(), summary.inserted(), summary.updated(),
                summary.skipped(), summary.conflicts(), summary.failed(), summary.durationMs());
        return summary;
    }

    private Outcome upsertOne(RawInstitution record, ImportOptions options) {
        if (record.name() == null || record.name().isBlank()) {
            return Outcome.SKIPPED;
        }
        String normalized = InstitutionService.normalize(record.name());
        if (normalized.isEmpty()) {
            return Outcome.SKIPPED;
        }

        Institution existing = null;
        if (notBlank(record.officialCode()) && notBlank(record.officialCodeType())) {
            existing = institutionRepository
                    .findByOfficialCodeTypeAndOfficialCode(record.officialCodeType().trim().toUpperCase(), record.officialCode().trim())
                    .orElse(null);
        }
        if (existing == null) {
            existing = (notBlank(record.state())
                    ? institutionRepository.findFirstByNormalizedNameAndState(normalized, record.state().trim())
                    : Optional.<Institution>empty())
                    .orElseGet(() -> institutionRepository.findFirstByNormalizedName(normalized).orElse(null));
        }

        boolean isNew = existing == null;
        if (isNew) {
            existing = new Institution();
        } else if (notBlank(record.officialCode()) && notBlank(existing.getOfficialCode())
                && !existing.getOfficialCode().equalsIgnoreCase(record.officialCode().trim())) {
            // Never silently overwrite conflicting official data (brief Part 7 §17).
            return Outcome.CONFLICT;
        }

        existing.setName(record.name().trim());
        existing.setNormalizedName(normalized);
        existing.setInstitutionType(validType(record.type()));
        if (notBlank(record.state())) existing.setState(record.state().trim());
        if (notBlank(record.district())) existing.setDistrict(record.district().trim());
        if (notBlank(record.city())) existing.setCity(record.city().trim());
        if (notBlank(record.officialCode())) existing.setOfficialCode(record.officialCode().trim());
        if (notBlank(record.officialCodeType())) existing.setOfficialCodeType(record.officialCodeType().trim().toUpperCase());
        existing.setSource(options.sourceName());
        if (options.sourceUrl() != null) existing.setSourceUrl(options.sourceUrl());
        existing.setSourceLastUpdated(Instant.now());
        if (existing.getVerificationStatus() == null || options.markVerified()) {
            existing.setVerificationStatus(options.markVerified() ? "VERIFIED" : "PENDING");
        }

        Institution saved = institutionRepository.save(existing);
        saveAliases(saved.getId(), record.aliases());
        return isNew ? Outcome.INSERTED : Outcome.UPDATED;
    }

    private void saveAliases(java.util.UUID institutionId, List<Alias> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }
        for (Alias alias : aliases) {
            if (alias.name() == null || alias.name().isBlank()) {
                continue;
            }
            String normalizedAlias = InstitutionService.normalize(alias.name());
            if (normalizedAlias.isEmpty()) {
                continue;
            }
            if (aliasRepository.findByInstitutionIdAndNormalizedAlias(institutionId, normalizedAlias).isPresent()) {
                continue;
            }
            InstitutionAlias entity = new InstitutionAlias();
            entity.setInstitutionId(institutionId);
            entity.setAliasName(alias.name().trim());
            entity.setNormalizedAlias(normalizedAlias);
            entity.setAliasType(validAliasType(alias.type()));
            aliasRepository.save(entity);
        }
    }

    // --- parsing -------------------------------------------------------------

    private RawInstitution fromJson(JsonNode node) {
        List<Alias> aliases = new ArrayList<>();
        JsonNode aliasArray = node.path("aliases");
        if (aliasArray.isArray()) {
            for (JsonNode a : aliasArray) {
                String aliasName = str(a, "name");
                if (aliasName != null) {
                    aliases.add(new Alias(aliasName, str(a, "type")));
                }
            }
        }
        return new RawInstitution(
                str(node, "name"),
                str(node, "type"),
                str(node, "officialCode"),
                str(node, "officialCodeType"),
                str(node, "state"),
                str(node, "district"),
                str(node, "city"),
                aliases);
    }

    private List<RawInstitution> parseCsv(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return List.of();
        }
        List<String> headers = splitCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(headers.get(i).trim().toLowerCase().replace(' ', '_'), i);
        }
        List<RawInstitution> records = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            List<String> cols = splitCsvLine(line);
            records.add(new RawInstitution(
                    column(cols, index, "name"),
                    column(cols, index, "type"),
                    column(cols, index, "official_code"),
                    column(cols, index, "official_code_type"),
                    column(cols, index, "state"),
                    column(cols, index, "district"),
                    column(cols, index, "city"),
                    parseAliases(column(cols, index, "aliases"))));
        }
        return records;
    }

    /** Splits one CSV line, honoring double-quoted fields and "" escapes (no embedded newlines). */
    static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    private static List<Alias> parseAliases(String raw) {
        List<Alias> aliases = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return aliases;
        }
        for (String part : raw.split("\\|")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            int colon = token.indexOf(':');
            if (colon > 0 && colon < token.length() - 1) {
                aliases.add(new Alias(token.substring(0, colon).trim(), token.substring(colon + 1).trim()));
            } else {
                aliases.add(new Alias(token, null));
            }
        }
        return aliases;
    }

    private static String column(List<String> cols, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i >= cols.size()) {
            return null;
        }
        String value = cols.get(i);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String str(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String validType(String type) {
        if (type == null) {
            return "OTHER";
        }
        String t = type.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        t = switch (t) {
            case "AUTONOMOUS" -> "AUTONOMOUS_COLLEGE";
            case "AFFILIATED" -> "AFFILIATED_COLLEGE";
            case "CONSTITUENT" -> "CONSTITUENT_COLLEGE";
            default -> t;
        };
        return INSTITUTION_TYPES.contains(t) ? t : "OTHER";
    }

    private static String validAliasType(String type) {
        if (type == null) {
            return "COMMON";
        }
        String t = type.trim().toUpperCase();
        return ALIAS_TYPES.contains(t) ? t : "COMMON";
    }

    private enum Outcome { INSERTED, UPDATED, SKIPPED, CONFLICT }

    record Alias(String name, String type) {
    }

    record RawInstitution(
            String name,
            String type,
            String officialCode,
            String officialCodeType,
            String state,
            String district,
            String city,
            List<Alias> aliases
    ) {
    }
}
