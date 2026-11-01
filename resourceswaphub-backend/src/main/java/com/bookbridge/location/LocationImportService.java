package com.bookbridge.location;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.bookbridge.shared.error.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Idempotent ingestion for the location directory (states, districts, cities, aliases).
 *
 * Matching is by official identifier first, then by (parent scope, normalized name), so a city is
 * never identified by its name alone and re-imports preserve canonical ids. A conflicting official
 * code is reported instead of overwriting stored data, and rows without an identifier are kept with
 * a null {@code official_code} rather than an invented one.
 *
 * Shapes: hierarchical JSON or flat CSV (see docs/LOCATION_DATA_SOURCES.md). Each state commits in
 * its own transaction so one bad row cannot abort a multi-thousand-row official import.
 */
@Service
public class LocationImportService {

    private static final Logger log = LoggerFactory.getLogger(LocationImportService.class);
    private static final Set<String> STATE_TYPES = Set.of("STATE", "UNION_TERRITORY");
    private static final Set<String> CITY_TYPES = Set.of(
            "METROPOLITAN_CITY", "CITY", "TOWN", "CENSUS_TOWN", "VILLAGE", "NOTIFICATION_AREA", "OTHER");
    private static final int MAX_REPORTED_ERRORS = 50;

    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;
    private final DistrictRepository districtRepository;
    private final CityRepository cityRepository;
    private final LocationAliasRepository aliasRepository;
    private final TransactionTemplate txTemplate;
    private final JsonMapper json = JsonMapper.builder().build();

    public LocationImportService(CountryRepository countryRepository,
                                 StateRepository stateRepository,
                                 DistrictRepository districtRepository,
                                 CityRepository cityRepository,
                                 LocationAliasRepository aliasRepository,
                                 PlatformTransactionManager transactionManager) {
        this.countryRepository = countryRepository;
        this.stateRepository = stateRepository;
        this.districtRepository = districtRepository;
        this.cityRepository = cityRepository;
        this.aliasRepository = aliasRepository;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public LocationImportSummary importFile(Path file, LocationImportOptions options) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        try (InputStream in = Files.newInputStream(file)) {
            if (name.endsWith(".json")) return importJson(in, options);
            if (name.endsWith(".csv")) return importCsv(in, options);
            throw new IllegalArgumentException("Unsupported file type (use .json or .csv): " + file);
        }
    }

    public LocationImportSummary importJson(InputStream in, LocationImportOptions options) throws IOException {
        JsonNode root = json.readTree(in);
        Country country = resolveCountry(text(root, "countryIsoCode"), text(root, "country"));
        List<RawState> states = new ArrayList<>();
        for (JsonNode node : root.path("states")) {
            states.add(stateFromJson(node));
        }
        return importStates(country, states, options);
    }

    public LocationImportSummary importCsv(InputStream in, LocationImportOptions options) throws IOException {
        Country country = resolveCountry(null, null);
        return importStates(country, parseCsv(in), options);
    }

    /**
     * A file must name a country that is already in the directory; the importer never invents one.
     * With no country given it falls back to India, the directory's default.
     */
    private Country resolveCountry(String isoCode, String name) {
        if (isoCode != null && !isoCode.isBlank()) {
            Optional<Country> byIso = countryRepository.findByIsoCode(isoCode.trim().toUpperCase());
            if (byIso.isPresent()) return byIso.get();
            throw ApiException.badRequest("UNKNOWN_COUNTRY",
                    "No directory record for country code '" + isoCode + "'; add it before importing");
        }
        if (name != null && !name.isBlank()) {
            return countryRepository.findFirstByNormalizedName(LocationService.normalize(name))
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_COUNTRY",
                            "No directory record for country '" + name + "'; add it before importing"));
        }
        return countryRepository.findByIsoCode("IN")
                .orElseThrow(() -> ApiException.badRequest("EMPTY_COUNTRY_DIRECTORY",
                        "The countries table has no India row; apply the location migration first"));
    }

    private LocationImportSummary importStates(Country country, List<RawState> states,
                                               LocationImportOptions options) {
        long started = System.currentTimeMillis();
        Counters totals = new Counters();
        for (RawState state : states) {
            Counters counters = new Counters();
            try {
                counters.states = 1;
                State saved = txTemplate.execute(status -> importOneState(country, state, options, counters));
                if (saved == null) counters.skipped++;
            } catch (RuntimeException ex) {
                counters.failed++;
                record(counters.errors, "state " + state.name() + ": " + ex.getMessage());
                log.warn("Location import failed for state '{}': {}", state.name(), ex.getMessage());
            }
            totals.add(counters);
        }
        LocationImportSummary summary = new LocationImportSummary(
                options.sourceName(), totals.states, totals.districts, totals.cities,
                totals.inserted, totals.updated, totals.skipped, totals.conflicts, totals.failed,
                System.currentTimeMillis() - started, totals.errors);
        log.info("Location import [{}]: states={} districts={} cities={} inserted={} updated={} "
                        + "skipped={} conflicts={} failed={} ({} ms)",
                summary.source(), summary.states(), summary.districts(), summary.cities(),
                summary.inserted(), summary.updated(), summary.skipped(), summary.conflicts(),
                summary.failed(), summary.durationMs());
        return summary;
    }

    private State importOneState(Country country, RawState raw, LocationImportOptions options,
                                 Counters counters) {
        String normalized = LocationService.normalize(raw.name());
        if (normalized.isEmpty()) return null;

        String code = upper(raw.officialCode());
        State state = code == null
                ? null
                : stateRepository.findFirstByCountryIdAndOfficialCode(country.getId(), code).orElse(null);
        if (state == null) {
            state = stateRepository.findFirstByCountryIdAndNormalizedName(country.getId(), normalized).orElse(null);
        }
        if (state != null && code != null && state.getOfficialCode() != null
                && !state.getOfficialCode().equalsIgnoreCase(code)) {
            counters.conflicts++;
            record(counters.errors, "state '" + raw.name() + "' already stored as " + state.getOfficialCode());
            return state;
        }
        boolean isNew = state == null;
        if (isNew) state = new State();
        state.setCountryId(country.getId());
        state.setName(raw.name().trim());
        state.setNormalizedName(normalized);
        state.setStateType(validStateType(raw.stateType()));
        if (code != null) state.setOfficialCode(code);
        applyProvenance(state::setSource, state::setSourceUrl, state::setDatasetVersion, options);
        state.setActive(true);
        state = stateRepository.save(state);
        if (isNew) counters.inserted++;
        else counters.updated++;
        counters.add(saveAliases(LocationService.ENTITY_STATE, state.getId(), raw.aliases()));

        for (RawDistrict rawDistrict : raw.districts()) {
            importDistrict(state, rawDistrict, options, counters);
        }
        for (RawCity rawCity : raw.cities()) {
            importCity(state, null, rawCity, options, counters);
        }
        return state;
    }

    private void importDistrict(State state, RawDistrict raw, LocationImportOptions options, Counters counters) {
        String normalized = LocationService.normalize(raw.name());
        if (normalized.isEmpty()) return;

        String code = upper(raw.officialCode());
        District district = code == null
                ? null
                : districtRepository.findFirstByStateIdAndOfficialCode(state.getId(), code).orElse(null);
        if (district == null) {
            district = districtRepository.findFirstByStateIdAndNormalizedName(state.getId(), normalized).orElse(null);
        }
        if (district != null && code != null && district.getOfficialCode() != null
                && !district.getOfficialCode().equalsIgnoreCase(code)) {
            counters.conflicts++;
            record(counters.errors, "district '" + raw.name() + "' in " + state.getName()
                    + " already stored as " + district.getOfficialCode());
            return;
        }
        boolean isNew = district == null;
        if (isNew) district = new District();
        district.setStateId(state.getId());
        district.setName(raw.name().trim());
        district.setNormalizedName(normalized);
        if (code != null) district.setOfficialCode(code);
        applyProvenance(district::setSource, district::setSourceUrl, district::setDatasetVersion, options);
        district.setActive(true);
        district = districtRepository.save(district);
        counters.districts++;
        if (isNew) counters.inserted++;
        else counters.updated++;
        counters.add(saveAliases(LocationService.ENTITY_DISTRICT, district.getId(), raw.aliases()));

        for (RawCity rawCity : raw.cities()) {
            importCity(state, district, rawCity, options, counters);
        }
    }

    private void importCity(State state, District district, RawCity raw, LocationImportOptions options,
                            Counters counters) {
        String normalized = LocationService.normalize(raw.name());
        if (normalized.isEmpty()) return;

        String code = upper(raw.officialCode());
        City city = code == null
                ? null
                : cityRepository.findFirstByStateIdAndOfficialCode(state.getId(), code).orElse(null);
        if (city == null) {
            city = cityRepository.findFirstByStateIdAndNormalizedName(state.getId(), normalized).orElse(null);
        }
        if (city != null && code != null && city.getOfficialCode() != null
                && !city.getOfficialCode().equalsIgnoreCase(code)) {
            counters.conflicts++;
            record(counters.errors, "city '" + raw.name() + "' in " + state.getName()
                    + " already stored as " + city.getOfficialCode());
            return;
        }
        boolean isNew = city == null;
        if (isNew) city = new City();
        city.setStateId(state.getId());
        // A district is attached only when the file names one; an existing link is never dropped.
        if (district != null && city.getDistrictId() == null) {
            city.setDistrictId(district.getId());
        }
        city.setName(raw.name().trim());
        city.setNormalizedName(normalized);
        city.setCityType(validCityType(raw.cityType()));
        if (code != null) city.setOfficialCode(code);
        applyProvenance(city::setSource, city::setSourceUrl, city::setDatasetVersion, options);
        city.setActive(true);
        city = cityRepository.save(city);
        counters.cities++;
        if (isNew) counters.inserted++;
        else counters.updated++;
        counters.add(saveAliases(LocationService.ENTITY_CITY, city.getId(), raw.aliases()));
    }

    private Counters saveAliases(String entityType, UUID entityId, List<String> aliases) {
        Counters counters = new Counters();
        if (aliases == null || aliases.isEmpty()) return counters;
        Set<String> present = aliasRepository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId)
                .stream()
                .map(LocationAlias::getNormalizedAlias)
                .collect(java.util.stream.Collectors.toSet());
        for (String alias : aliases) {
            String normalized = LocationService.normalize(alias);
            if (normalized.isEmpty() || !present.add(normalized)) continue;
            LocationAlias entity = new LocationAlias();
            entity.setEntityType(entityType);
            entity.setEntityId(entityId);
            entity.setAlias(alias.trim());
            entity.setNormalizedAlias(normalized);
            aliasRepository.save(entity);
            counters.inserted++;
        }
        return counters;
    }

    private static void applyProvenance(Consumer<String> source, Consumer<String> sourceUrl,
                                        Consumer<String> datasetVersion, LocationImportOptions options) {
        source.accept(options.sourceName());
        sourceUrl.accept(options.sourceUrl());
        datasetVersion.accept(options.datasetVersion());
    }

    // --- parsing -------------------------------------------------------------

    private RawState stateFromJson(JsonNode node) {
        List<RawCity> directCities = new ArrayList<>();
        for (JsonNode cityNode : node.path("cities")) {
            directCities.add(cityFromJson(cityNode));
        }
        List<RawDistrict> districts = new ArrayList<>();
        for (JsonNode districtNode : node.path("districts")) {
            List<RawCity> cities = new ArrayList<>();
            for (JsonNode cityNode : districtNode.path("cities")) {
                cities.add(cityFromJson(cityNode));
            }
            districts.add(new RawDistrict(
                    text(districtNode, "name"),
                    firstNonBlank(text(districtNode, "code"), text(districtNode, "officialCode")),
                    stringList(districtNode.path("aliases")),
                    cities));
        }
        return new RawState(
                text(node, "name"),
                firstNonBlank(text(node, "code"), text(node, "officialCode")),
                firstNonBlank(text(node, "type"), text(node, "stateType")),
                stringList(node.path("aliases")),
                districts,
                directCities);
    }

    private RawCity cityFromJson(JsonNode node) {
        return new RawCity(
                text(node, "name"),
                firstNonBlank(text(node, "code"), text(node, "officialCode")),
                firstNonBlank(text(node, "type"), text(node, "cityType")),
                stringList(node.path("aliases")));
    }

    /**
     * Flat CSV, one row per city:
     * {@code state_code,state_name,state_type,district_code,district_name,city_code,city_name,city_type,city_aliases}.
     * Rows sharing a state or district reuse the directory record instead of duplicating it.
     */
    private List<RawState> parseCsv(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String headerLine = reader.readLine();
        if (headerLine == null) return List.of();
        List<String> headers = splitCsvLine(headerLine);
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(headers.get(i).trim().toLowerCase().replace(' ', '_'), i);
        }

        Map<String, CsvState> byState = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            List<String> cols = splitCsvLine(line);
            String stateName = column(cols, index, "state_name");
            if (stateName == null) continue;
            CsvState state = byState.computeIfAbsent(
                    firstNonBlank(column(cols, index, "state_code"), stateName).toUpperCase(),
                    key -> new CsvState(stateName, column(cols, index, "state_code"), column(cols, index, "state_type")));

            String districtName = column(cols, index, "district_name");
            RawCity city = new RawCity(
                    column(cols, index, "city_name"),
                    column(cols, index, "city_code"),
                    column(cols, index, "city_type"),
                    splitAliases(column(cols, index, "city_aliases")));
            if (city.name() == null) continue;
            if (districtName == null) {
                state.cities.add(city);
                continue;
            }
            CsvDistrict district = state.districts.computeIfAbsent(
                    districtName.toLowerCase(),
                    key -> new CsvDistrict(districtName, column(cols, index, "district_code")));
            district.cities.add(city);
        }

        List<RawState> states = new ArrayList<>();
        for (CsvState state : byState.values()) {
            List<RawDistrict> districts = new ArrayList<>();
            for (CsvDistrict district : state.districts.values()) {
                districts.add(new RawDistrict(district.name, district.code, List.of(), List.copyOf(district.cities)));
            }
            states.add(new RawState(state.name, state.code, state.type, List.of(), districts,
                    List.copyOf(state.cities)));
        }
        return states;
    }

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

    private static List<String> splitAliases(String raw) {
        List<String> aliases = new ArrayList<>();
        if (raw == null || raw.isBlank()) return aliases;
        for (String part : raw.split("\\|")) {
            String token = part.trim();
            if (!token.isEmpty()) aliases.add(token);
        }
        return aliases;
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.isObject() ? text(item, "name") : item.asString();
                if (value != null) values.add(value);
            }
        }
        return values;
    }

    private static String column(List<String> cols, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i >= cols.size()) return null;
        String value = cols.get(i);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String s = value.asString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static String validStateType(String type) {
        String candidate = type == null ? null : type.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        if ("UT".equals(candidate) || "UNION_TERRITORY".equals(candidate)) return "UNION_TERRITORY";
        return STATE_TYPES.contains(candidate) ? candidate : "STATE";
    }

    private static String validCityType(String type) {
        if (type == null || type.isBlank()) return null;
        String candidate = type.trim().toUpperCase().replace(' ', '_').replace('-', '_');
        return CITY_TYPES.contains(candidate) ? candidate : "OTHER";
    }

    private static void record(List<String> errors, String message) {
        if (errors.size() < MAX_REPORTED_ERRORS) errors.add(message);
    }

    private static final class CsvState {
        private final String name;
        private final String code;
        private final String type;
        private final Map<String, CsvDistrict> districts = new LinkedHashMap<>();
        private final List<RawCity> cities = new ArrayList<>();

        CsvState(String name, String code, String type) {
            this.name = name;
            this.code = code;
            this.type = type;
        }
    }

    private static final class CsvDistrict {
        private final String name;
        private final String code;
        private final List<RawCity> cities = new ArrayList<>();

        CsvDistrict(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }

    private static final class Counters {
        private final List<String> errors = new ArrayList<>();
        int states;
        int districts;
        int cities;
        int inserted;
        int updated;
        int skipped;
        int conflicts;
        int failed;

        void add(Counters other) {
            if (other == null) return;
            states += other.states;
            districts += other.districts;
            cities += other.cities;
            inserted += other.inserted;
            updated += other.updated;
            skipped += other.skipped;
            conflicts += other.conflicts;
            failed += other.failed;
            for (String error : other.errors) {
                if (errors.size() < MAX_REPORTED_ERRORS) errors.add(error);
            }
        }
    }

    record RawState(String name, String officialCode, String stateType, List<String> aliases,
                    List<RawDistrict> districts, List<RawCity> cities) {
    }

    record RawDistrict(String name, String officialCode, List<String> aliases, List<RawCity> cities) {
    }

    record RawCity(String name, String officialCode, String cityType, List<String> aliases) {
    }
}
