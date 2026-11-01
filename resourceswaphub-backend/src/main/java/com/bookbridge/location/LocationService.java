package com.bookbridge.location;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookbridge.shared.api.PageResponse;
import com.bookbridge.shared.error.ApiException;

/**
 * Read side of the standardized location directory. Every city query is scoped to a state so a
 * name that exists in two states can never be resolved by name alone.
 */
@Service
public class LocationService {

    public static final String ENTITY_COUNTRY = "COUNTRY";
    public static final String ENTITY_STATE = "STATE";
    public static final String ENTITY_DISTRICT = "DISTRICT";
    public static final String ENTITY_CITY = "CITY";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final CountryRepository countryRepository;
    private final StateRepository stateRepository;
    private final DistrictRepository districtRepository;
    private final CityRepository cityRepository;
    private final LocationAliasRepository aliasRepository;

    public LocationService(CountryRepository countryRepository,
                           StateRepository stateRepository,
                           DistrictRepository districtRepository,
                           CityRepository cityRepository,
                           LocationAliasRepository aliasRepository) {
        this.countryRepository = countryRepository;
        this.stateRepository = stateRepository;
        this.districtRepository = districtRepository;
        this.cityRepository = cityRepository;
        this.aliasRepository = aliasRepository;
    }

    @Transactional(readOnly = true)
    public List<CountryDto> countries(String q) {
        String query = normalize(q);
        List<Country> countries = query.isEmpty()
                ? countryRepository.findByActiveTrueOrderByIsoCodeAsc()
                : countryRepository.searchActive(query);
        return countries.stream().map(CountryDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<StateDto> states(UUID countryId, String q) {
        Country country = requireCountry(countryId);
        String query = normalize(q);
        return stateRepository.findByCountryIdAndActiveTrueOrderByNormalizedNameAsc(country.getId())
                .stream()
                .filter(state -> query.isEmpty() || matches(state, query))
                .map(StateDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DistrictDto> districts(UUID stateId, String q) {
        State state = requireState(stateId);
        String query = normalize(q);
        return districtRepository.findByStateIdAndActiveTrueOrderByNormalizedNameAsc(state.getId())
                .stream()
                .filter(district -> query.isEmpty()
                        || district.getNormalizedName().contains(query)
                        || normalizedAliases(ENTITY_DISTRICT, district.getId()).stream()
                                .anyMatch(alias -> alias.contains(query)))
                .map(DistrictDto::from)
                .toList();
    }

    /**
     * Cities are only ever listed inside one state and always paginated — the directory holds
     * thousands of rows per state and is never returned in one response.
     */
    @Transactional(readOnly = true)
    public PageResponse<CityDto> cities(UUID stateId, String q, Pageable pageable) {
        State state = requireState(stateId);
        String query = normalize(q);
        int page = Math.max(pageable.getPageNumber(), 0);
        int size = pageable.getPageSize() <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        List<City> cities = cityRepository.searchInState(state.getId(), query,
                Pageable.ofSize(size).withPage(page));
        long total = cityRepository.countSearchInState(state.getId(), query);
        int totalPages = (int) Math.ceil((double) total / size);
        List<CityDto> content = cities.stream()
                .map(city -> CityDto.from(city, state.getName(), districtNameOf(city)))
                .toList();
        return new PageResponse<>(content, page, size, total, totalPages, page + 1 < totalPages);
    }

    @Transactional(readOnly = true)
    public CityDto city(UUID id) {
        City city = cityRepository.findById(id).orElseThrow(() -> ApiException.notFound("City not found"));
        State state = stateRepository.findById(city.getStateId())
                .orElseThrow(() -> ApiException.notFound("City not found"));
        return CityDto.from(city, state.getName(), districtNameOf(city));
    }

    // ------------------------------------------------------------------------
    // Validation + resolution used by profile, listing and backfill writes.
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Country requireCountry(UUID id) {
        if (id == null) throw ApiException.badRequest("INVALID_COUNTRY", "countryId is required");
        return countryRepository.findById(id)
                .filter(Country::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_COUNTRY", "Unknown or inactive countryId"));
    }

    @Transactional(readOnly = true)
    public State requireState(UUID id) {
        if (id == null) throw ApiException.badRequest("INVALID_STATE", "stateId is required");
        return stateRepository.findById(id)
                .filter(State::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_STATE", "Unknown or inactive stateId"));
    }

    @Transactional(readOnly = true)
    public City requireCity(UUID id) {
        if (id == null) throw ApiException.badRequest("INVALID_CITY", "cityId is required");
        return cityRepository.findById(id)
                .filter(City::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_CITY", "Unknown or inactive cityId"));
    }

    @Transactional(readOnly = true)
    public District requireDistrict(UUID id) {
        if (id == null) throw ApiException.badRequest("INVALID_DISTRICT", "districtId is required");
        return districtRepository.findById(id)
                .filter(District::isActive)
                .orElseThrow(() -> ApiException.badRequest("INVALID_DISTRICT", "Unknown or inactive districtId"));
    }

    /**
     * Maps free-text to a canonical state: exact normalized name first, then a recorded alias,
     * then a unique substring match. Returns empty on anything ambiguous so the caller never
     * guesses a location.
     */
    @Transactional(readOnly = true)
    public Optional<State> resolveState(UUID countryId, String text) {
        String query = normalize(text);
        if (query.isEmpty()) return Optional.empty();
        Optional<State> exact = stateRepository.findFirstByCountryIdAndNormalizedName(countryId, query);
        if (exact.isPresent()) return exact;
        List<UUID> aliasTargets = aliasRepository.findEntityIdsByAlias(ENTITY_STATE, query);
        Optional<State> aliased = stateRepository.findAllById(aliasTargets).stream()
                .filter(state -> state.getCountryId().equals(countryId) && state.isActive())
                .findFirst();
        if (aliased.isPresent()) return aliased;
        List<State> partial = stateRepository.findByCountryIdAndActiveTrueOrderByNormalizedNameAsc(countryId)
                .stream()
                .filter(state -> state.getNormalizedName().contains(query))
                .toList();
        return partial.size() == 1 ? Optional.of(partial.get(0)) : Optional.empty();
    }

    /** Maps free-text to a canonical city inside one state; empty when nothing is unambiguous. */
    @Transactional(readOnly = true)
    public Optional<City> resolveCity(UUID stateId, String text) {
        String query = normalize(text);
        if (query.isEmpty()) return Optional.empty();
        Optional<City> exact = cityRepository.findFirstByStateIdAndNormalizedName(stateId, query);
        if (exact.isPresent()) return exact;
        List<UUID> aliasTargets = aliasRepository.findEntityIdsByAlias(ENTITY_CITY, query);
        Optional<City> aliased = cityRepository.findAllById(aliasTargets).stream()
                .filter(city -> city.getStateId().equals(stateId) && city.isActive())
                .findFirst();
        if (aliased.isPresent()) return aliased;
        List<City> partial = cityRepository.findByStateIdAndActiveTrueOrderByNormalizedNameAsc(stateId)
                .stream()
                .filter(city -> city.getNormalizedName().contains(query))
                .toList();
        return partial.size() == 1 ? Optional.of(partial.get(0)) : Optional.empty();
    }

    /**
     * Single write-side entry point for profile, listing, collection and backfill code: takes
     * whichever of the standardized ids or free-text values a caller has and produces the ids plus
     * the canonical name text to store alongside them. Ids always win; text is resolved only when
     * it maps to exactly one directory record, so an ambiguous name never assigns a location.
     */
    @Transactional(readOnly = true)
    public ResolvedLocation resolve(UUID countryId, UUID stateId, UUID cityId,
                                    String cityText, String stateText, String countryText) {
        if (cityId != null) {
            City city = requireCity(cityId);
            State state = requireState(city.getStateId());
            if (stateId != null && !state.getId().equals(stateId)) {
                throw ApiException.badRequest("CITY_STATE_MISMATCH", "cityId does not belong to stateId");
            }
            Country country = requireCountry(state.getCountryId());
            if (countryId != null && !country.getId().equals(countryId)) {
                throw ApiException.badRequest("CITY_COUNTRY_MISMATCH", "cityId does not belong to countryId");
            }
            return new ResolvedLocation(country.getId(), state.getId(), city.getId(),
                    country.getName(), state.getName(), city.getName(), null);
        }

        if (stateId != null) {
            State state = requireState(stateId);
            Country country = requireCountry(state.getCountryId());
            City city = resolveCity(state.getId(), cityText).orElse(null);
            return new ResolvedLocation(country.getId(), state.getId(),
                    city == null ? null : city.getId(),
                    country.getName(), state.getName(),
                    city == null ? blankToNull(cityText) : city.getName(), null);
        }

        Country country = countryId != null
                ? requireCountry(countryId)
                : resolveCountryByName(countryText).orElse(null);
        if (country == null) {
            return ResolvedLocation.empty(blankToNull(cityText), blankToNull(stateText), blankToNull(countryText));
        }
        State state = resolveState(country.getId(), stateText).orElse(null);
        City city = null;
        if (state != null) {
            city = resolveCity(state.getId(), cityText).orElse(null);
        } else {
            // No usable state text: a name only identifies a place when the whole directory
            // contains it exactly once, otherwise the state stays unknown.
            city = resolveCityAcrossDirectory(cityText).orElse(null);
            state = city == null ? null : requireState(city.getStateId());
        }
        if (state == null) {
            return new ResolvedLocation(country.getId(), null, null,
                    country.getName(), blankToNull(stateText), blankToNull(cityText), country.getId());
        }
        return new ResolvedLocation(country.getId(), state.getId(),
                city == null ? null : city.getId(),
                country.getName(), state.getName(),
                city == null ? blankToNull(cityText) : city.getName(),
                country.getId());
    }

    /** A city name with no state beside it is only usable when no other state has that city. */
    @Transactional(readOnly = true)
    public Optional<City> resolveCityAcrossDirectory(String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return Optional.empty();
        }
        List<City> matches = cityRepository.findByNormalizedNameAndActiveTrue(query);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private Optional<Country> resolveCountryByName(String text) {
        String query = normalize(text);
        if (query.isEmpty()) {
            return countryRepository.findByIsoCode("IN");
        }
        Optional<Country> byName = countryRepository.findFirstByNormalizedName(query);
        if (byName.isPresent()) return byName;
        return countryRepository.findByIsoCode(query.toUpperCase());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The standardized ids plus the text to keep beside them. {@code unresolvedCountryId} is set when a
     * country was recognised but its state or city text could not be pinned to one record, which
     * is what the backfill runner reports instead of guessing.
     */
    public record ResolvedLocation(
            UUID countryId,
            UUID stateId,
            UUID cityId,
            String countryName,
            String stateName,
            String cityName,
            UUID unresolvedCountryId
    ) {
        public static ResolvedLocation empty(String cityText, String stateText, String countryText) {
            return new ResolvedLocation(null, null, null, countryText, stateText, cityText, null);
        }

        public boolean matchedState() {
            return stateId != null;
        }

        public boolean matchedCity() {
            return cityId != null;
        }
    }

    /**
     * The names for one stored location, read from the directory so a page of records shows
     * canonical spellings rather than whatever text was typed when it was created.
     */
    @Transactional(readOnly = true)
    public LocationDto describe(UUID countryId, UUID stateId, UUID cityId) {
        return index(idList(countryId), idList(stateId), idList(cityId)).of(countryId, stateId, cityId);
    }

    /**
     * Batched variant of {@link #describe}: three primary-key reads for a whole page instead of
     * three per row. Null ids are simply skipped.
     */
    @Transactional(readOnly = true)
    public LocationIndex index(Collection<UUID> countryIds, Collection<UUID> stateIds, Collection<UUID> cityIds) {
        Map<UUID, Country> countries = countryRepository.findAllById(distinctIds(countryIds)).stream()
                .collect(Collectors.toMap(Country::getId, country -> country));
        Map<UUID, State> states = stateRepository.findAllById(distinctIds(stateIds)).stream()
                .collect(Collectors.toMap(State::getId, state -> state));
        Map<UUID, City> cities = cityRepository.findAllById(distinctIds(cityIds)).stream()
                .collect(Collectors.toMap(City::getId, city -> city));
        return new LocationIndex(countries, states, cities);
    }

    private static List<UUID> idList(UUID id) {
        return id == null ? List.of() : List.of(id);
    }

    private static List<UUID> distinctIds(Collection<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    public record LocationIndex(
            Map<UUID, Country> countries,
            Map<UUID, State> states,
            Map<UUID, City> cities
    ) {
        public LocationDto of(UUID countryId, UUID stateId, UUID cityId) {
            City city = cityId == null ? null : cities.get(cityId);
            State state = stateId == null
                    ? (city == null ? null : states.get(city.getStateId()))
                    : states.get(stateId);
            Country country = countryId == null
                    ? (state == null ? null : countries.get(state.getCountryId()))
                    : countries.get(countryId);
            return new LocationDto(
                    country == null ? null : country.getId(),
                    state == null ? null : state.getId(),
                    city == null ? null : city.getId(),
                    country == null ? null : country.getName(),
                    state == null ? null : state.getName(),
                    city == null ? null : city.getName()
            );
        }
    }

    private String districtNameOf(City city) {        if (city.getDistrictId() == null) return null;
        return districtRepository.findById(city.getDistrictId()).map(District::getName).orElse(null);
    }

    private boolean matches(State state, String query) {
        return state.getNormalizedName().contains(query)
                || normalizedAliases(ENTITY_STATE, state.getId()).stream().anyMatch(alias -> alias.contains(query));
    }

    private List<String> normalizedAliases(String entityType, UUID entityId) {
        return aliasRepository.findByEntityTypeAndEntityIdOrderByIdAsc(entityType, entityId).stream()
                .map(LocationAlias::getNormalizedAlias)
                .toList();
    }

    public static String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
