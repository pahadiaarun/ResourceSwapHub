package com.bookbridge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bookbridge.location.LocationBackfillService;

import tools.jackson.databind.JsonNode;

/**
 * Integration coverage for the standardized location directory: the cascading read APIs, the
 * id-based writes profiles and listings make, and the backfill pass over pre-directory free text.
 * Everything runs over real HTTP against the seeded directory so the "same choice resolves to the
 * same record" rule is validated against the rows users actually see.
 */
class LocationIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private LocationBackfillService backfillService;

    private static final List<String> STATES = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat",
            "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
            "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
            "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
            "Uttarakhand", "West Bengal");

    private static final List<String> UNION_TERRITORIES = List.of(
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    // ---------------------------------------------------------------------
    // Directory reads
    // ---------------------------------------------------------------------

    @Test
    void directoryCoversEveryStateAndUnionTerritory() {
        var countries = api.get("/api/v1/locations/countries", null);
        assertThat(countries.status()).isEqualTo(200);
        assertThat(strings(countries.data(), "isoCode")).contains("IN");
        // India is the default, never the only supported country.
        assertThat(strings(countries.data(), "name")).contains("India", "United States");

        var states = api.get("/api/v1/locations/states?countryId=" + indiaId(), null);
        assertThat(states.status()).as("states: %s", states.body()).isEqualTo(200);
        List<String> names = strings(states.data(), "name");
        assertThat(names).hasSize(STATES.size() + UNION_TERRITORIES.size())
                .containsAll(STATES)
                .containsAll(UNION_TERRITORIES);
        assertThat(countTypes(states.data(), "STATE")).isEqualTo(28);
        assertThat(countTypes(states.data(), "UNION_TERRITORY")).isEqualTo(8);
        assertThat(strings(states.data(), "officialCode")).contains("IN-MH", "IN-DL", "IN-PY");
    }

    @Test
    void countrySearchIgnoresCaseAndFindsByIsoCode() {
        assertThat(strings(api.get("/api/v1/locations/countries?q=india", null).data(), "name"))
                .containsExactly("India");
        assertThat(strings(api.get("/api/v1/locations/countries?q=IND", null).data(), "isoCode"))
                .containsExactly("IN");
        assertThat(strings(api.get("/api/v1/locations/countries?q=zzzz", null).data(), "name"))
                .isEmpty();
    }

    @Test
    void stateSearchIsCaseInsensitiveAndAcceptsRecordedSpellings() {
        String base = "/api/v1/locations/states?countryId=" + indiaId();
        assertThat(strings(api.get(base + "&q=KERALA", null).data(), "name")).containsExactly("Kerala");
        assertThat(strings(api.get(base + "&q=kerala", null).data(), "name")).containsExactly("Kerala");
        assertThat(strings(api.get(base + "&q=TeLangAna", null).data(), "name")).containsExactly("Telangana");
        // Former and short names are aliases, not separate rows.
        assertThat(strings(api.get(base + "&q=Orissa", null).data(), "name")).containsExactly("Odisha");
        assertThat(strings(api.get(base + "&q=NCT", null).data(), "name")).containsExactly("Delhi");
        // A substring shared by several states returns all of them for the user to choose from.
        assertThat(strings(api.get(base + "&q=pradesh", null).data(), "name"))
                .contains("Andhra Pradesh", "Arunachal Pradesh", "Madhya Pradesh", "Uttar Pradesh")
                .doesNotContain("Kerala");
    }

    @Test
    void citiesAreListedOnlyInsideOneStateAndAlwaysPaged() {
        UUID maharashtra = stateId("Maharashtra");
        var first = api.get(citiesPath(maharashtra, null, null, 5, 0), null);
        assertThat(first.status()).as("cities: %s", first.body()).isEqualTo(200);
        JsonNode page = first.data();
        assertThat(strings(page.path("content"), "name")).hasSize(5);
        assertThat(page.path("totalElements").asLong()).isGreaterThan(5);
        assertThat(page.path("hasNext").asBoolean()).isTrue();
        // No row can leak in from another state, and the whole directory is never returned.
        for (JsonNode city : page.path("content")) {
            assertThat(city.path("stateId").asString()).isEqualTo(maharashtra.toString());
            assertThat(city.path("stateName").asString()).isEqualTo("Maharashtra");
        }

        var second = api.get(citiesPath(maharashtra, null, null, 5, 1), null);
        assertThat(strings(second.data().path("content"), "name"))
                .doesNotContainAnyElementsOf(strings(page.path("content"), "name"));

        var searched = api.get(citiesPath(maharashtra, "pune", null, 20, 0), null);
        assertThat(strings(searched.data().path("content"), "name")).containsExactly("Pune");
        assertThat(searched.data().path("content").path(0).path("districtName").asString())
                .isEqualTo("Pune");
    }

    @Test
    void sameCityNameInTwoStatesStaysTwoDistinctRecords() {
        UUID rajasthan = stateId("Rajasthan");
        UUID tripura = stateId("Tripura");

        var inRajasthan = api.get(citiesPath(rajasthan, "udaipur", null, 20, 0), null);
        var inTripura = api.get(citiesPath(tripura, "udaipur", null, 20, 0), null);

        // The exact match ranks first, but tolerant search also offers near neighbours (Jaipur) to
        // correct a mistyped name — every row still belongs to the state that was asked for.
        assertThat(strings(inRajasthan.data().path("content"), "name")).startsWith("Udaipur");
        assertThat(strings(inTripura.data().path("content"), "name")).startsWith("Udaipur");
        for (JsonNode city : inRajasthan.data().path("content")) {
            assertThat(city.path("stateId").asString()).isEqualTo(rajasthan.toString());
        }

        JsonNode rajasthanCity = named(inRajasthan.data().path("content"), "Udaipur");
        JsonNode tripuraCity = named(inTripura.data().path("content"), "Udaipur");
        assertThat(rajasthanCity.path("stateName").asString()).isEqualTo("Rajasthan");
        assertThat(tripuraCity.path("stateName").asString()).isEqualTo("Tripura");
        assertThat(rajasthanCity.path("id").asString()).isNotEqualTo(tripuraCity.path("id").asString());

        var detail = api.get("/api/v1/locations/cities/" + rajasthanCity.path("id").asString(), null);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.data().path("stateId").asString()).isEqualTo(rajasthan.toString());
        assertThat(detail.data().path("name").asString()).isEqualTo("Udaipur");
    }

    @Test
    void unknownLocationIdsAndMissingRowsAreRejected() {
        UUID bogus = UUID.randomUUID();

        var badCountry = api.get("/api/v1/locations/states?countryId=" + bogus, null);
        assertThat(badCountry.status()).isEqualTo(400);
        assertThat(badCountry.body().path("error").path("code").asString()).isEqualTo("INVALID_COUNTRY");

        var badState = api.get(citiesPath(bogus, null, null, 20, 0), null);
        assertThat(badState.status()).isEqualTo(400);
        assertThat(badState.body().path("error").path("code").asString()).isEqualTo("INVALID_STATE");

        var badDistricts = api.get("/api/v1/locations/districts?stateId=" + bogus, null);
        assertThat(badDistricts.status()).isEqualTo(400);
        assertThat(badDistricts.body().path("error").path("code").asString()).isEqualTo("INVALID_STATE");

        assertThat(api.get("/api/v1/locations/cities/" + bogus, null).status()).isEqualTo(404);

        // A city pinned to the wrong state is a client error, never a silent re-parenting.
        Session session = register("Gita");
        var mismatch = api.patch("/api/v1/users/me", Map.of(
                "name", "Gita",
                "stateId", stateId("Tripura").toString(),
                "cityId", cityId("Maharashtra", "Pune").toString()
        ), session.token());
        assertThat(mismatch.status()).as("mismatch body: %s", mismatch.body()).isEqualTo(400);
        assertThat(mismatch.body().path("error").path("code").asString())
                .isEqualTo("CITY_STATE_MISMATCH");
    }

    @Test
    void emptyCityResultsSurfaceAsAnEmptyPage() {
        UUID lakshadweep = stateId("Lakshadweep");
        var noMatch = api.get(citiesPath(lakshadweep, "notacityatall", null, 20, 0), null);
        assertThat(noMatch.status()).isEqualTo(200);
        assertThat(strings(noMatch.data().path("content"), "name")).isEmpty();
        assertThat(noMatch.data().path("totalElements").asLong()).isZero();
        assertThat(noMatch.data().path("hasNext").asBoolean()).isFalse();

        UUID maharashtra = stateId("Maharashtra");
        var pastTheEnd = api.get(citiesPath(maharashtra, null, null, 5, 99), null);
        assertThat(strings(pastTheEnd.data().path("content"), "name")).isEmpty();
        assertThat(pastTheEnd.data().path("totalElements").asLong()).isGreaterThan(0);
    }

    // ---------------------------------------------------------------------
    // Writes: ids and free text must land on the same directory record
    // ---------------------------------------------------------------------

    @Test
    void theSamePlaceResolvesToTheSameRecordWhetherChosenOrTyped() {
        UUID maharashtra = stateId("Maharashtra");
        UUID pune = cityId("Maharashtra", "Pune");

        Session chosen = register("Asha");
        assertThat(api.patch("/api/v1/users/me", Map.of(
                "name", "Asha",
                "locality", "Kothrud",
                "stateId", maharashtra.toString(),
                "cityId", pune.toString()
        ), chosen.token()).status()).isEqualTo(200);

        // Typed text goes through the alias table: with the state chosen, "Poona" is Pune.
        Session typed = register("Bilal");
        var typedResponse = api.patch("/api/v1/users/me", Map.of(
                "name", "Bilal",
                "locality", "Camp",
                "stateId", maharashtra.toString(),
                "city", "Poona"
        ), typed.token());
        assertThat(typedResponse.status()).as("typed profile: %s", typedResponse.body()).isEqualTo(200);

        assertThat(userLocation(chosen.id())).isEqualTo(userLocation(typed.id()));
        assertThat(column(typed.id(), "city_id")).isEqualTo(pune.toString());
        assertThat(column(typed.id(), "state")).isEqualTo("Maharashtra");
        assertThat(column(typed.id(), "city")).isEqualTo("Pune");
        assertThat(typedResponse.data().path("location").path("cityName").asString()).isEqualTo("Pune");
    }

    @Test
    void ambiguousBareCityNameIsKeptAsTextInsteadOfGuessed() {
        Session session = register("Chen");
        var response = api.patch("/api/v1/users/me", Map.of(
                "name", "Chen",
                "locality", "MG Road",
                "city", "Udaipur"
        ), session.token());
        assertThat(response.status()).as("bare city: %s", response.body()).isEqualTo(200);

        // Udaipur exists in two states, so no city row may be attached.
        assertThat(column(session.id(), "city_id")).isNull();
        assertThat(column(session.id(), "state_id")).isNull();
        // ...and the typed value is preserved rather than dropped.
        assertThat(column(session.id(), "city")).isEqualTo("Udaipur");
    }

    @Test
    void discoveryFiltersByStandardizedIdsAndCombineWithOtherFilters() {
        UUID maharashtra = stateId("Maharashtra");
        UUID pune = cityId("Maharashtra", "Pune");
        UUID rajasthan = stateId("Rajasthan");
        UUID udaipur = cityId("Rajasthan", "Udaipur");

        Session puneSeller = registerWithLocation("Pune Seller", maharashtra, pune);
        Session udaipurSeller = registerWithLocation("Udaipur Seller", rajasthan, udaipur);

        UUID puneListing = createListingAt(puneSeller, uniqueTitle("Geometry"), maharashtra, pune, "Pune");
        UUID udaipurListing = createListingAt(udaipurSeller, uniqueTitle("Algebra"), rajasthan, udaipur, "Udaipur");

        var byCity = api.get("/api/v1/discovery/listings?cityId=" + pune, puneSeller.token());
        assertThat(strings(byCity.data().path("content"), "id")).containsExactly(puneListing.toString());

        var byState = api.get("/api/v1/discovery/listings?stateId=" + maharashtra, puneSeller.token());
        assertThat(strings(byState.data().path("content"), "id")).containsExactly(puneListing.toString());

        // Standardized filters stack with the existing ones.
        var fixedPrice = api.get("/api/v1/discovery/listings?cityId=" + pune
                + "&priceType=FIXED_PRICE", puneSeller.token());
        assertThat(strings(fixedPrice.data().path("content"), "id")).containsExactly(puneListing.toString());
        var freeOnly = api.get("/api/v1/discovery/listings?cityId=" + pune
                + "&priceType=FREE", puneSeller.token());
        assertThat(strings(freeOnly.data().path("content"), "id")).isEmpty();

        // Legacy text filtering keeps working against the mirrored column.
        var legacyCity = api.get("/api/v1/discovery/listings?city=pune", puneSeller.token());
        assertThat(strings(legacyCity.data().path("content"), "id")).containsExactly(puneListing.toString());

        // Rows show canonical names, and an unknown id is an error rather than an empty page.
        assertThat(byCity.data().path("content").path(0).path("location").path("stateName").asString())
                .isEqualTo("Maharashtra");
        assertThat(api.get("/api/v1/discovery/listings?cityId=" + UUID.randomUUID(),
                puneSeller.token()).status()).isEqualTo(400);
    }

    // ---------------------------------------------------------------------
    // Backfill
    // ---------------------------------------------------------------------

    @Test
    void backfillStandardizesFreeTextAndLeavesUnmatchedRowsAlone() {
        Session textOnly = register("Deepa");
        Session unmatchable = register("Evan");
        Session staleText = register("Farid");

        UUID india = indiaId();
        UUID maharashtra = stateId("Maharashtra");
        UUID pune = cityId("Maharashtra", "Pune");

        // Pre-directory rows: free text only.
        jdbc.update("update users set country = 'India', state = null, city = 'Bengaluru', "
                + "country_id = null, state_id = null, city_id = null where id = ?", textOnly.id());
        jdbc.update("update users set country = 'India', state = 'Atlantis', city = 'Nowhereville', "
                + "country_id = null, state_id = null, city_id = null where id = ?", unmatchable.id());
        // Rows this pass touched before: linked ids, but the mirrored text was never written.
        jdbc.update("update users set country = 'India', state = null, city = 'Pune', "
                + "country_id = ?, state_id = ?, city_id = ? where id = ?",
                india, maharashtra, pune, staleText.id());

        var first = backfillService.run();

        assertThat(column(textOnly.id(), "state_id")).isEqualTo(stateId("Karnataka").toString());
        assertThat(column(textOnly.id(), "city")).isEqualTo("Bengaluru");
        // Unmatched text is kept verbatim so a later import can place it.
        assertThat(column(unmatchable.id(), "city")).isEqualTo("Nowhereville");
        assertThat(column(unmatchable.id(), "state_id")).isNull();
        // The stale row is revisited and its text re-mirrored from the directory.
        assertThat(column(staleText.id(), "state")).isEqualTo("Maharashtra");
        assertThat(first.unmatchedSamples()).anyMatch(sample -> sample.contains("Nowhereville"));
        assertThat(first.usersMatched()).isGreaterThanOrEqualTo(2);

        // Re-running only revisits the row it could not place; nothing already standardized is touched.
        var second = backfillService.run();
        assertThat(second.usersMatched()).isZero();
        assertThat(second.usersScanned()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String citiesPath(UUID stateId, String q, String sort, int size, int page) {
        StringBuilder path = new StringBuilder("/api/v1/locations/cities?stateId=").append(stateId);
        if (q != null) {
            path.append("&q=").append(q);
        }
        if (sort != null) {
            path.append("&sort=").append(sort);
        }
        return path.append("&size=").append(size).append("&page=").append(page).toString();
    }

    private UUID indiaId() {
        return UUID.fromString(jdbc.queryForObject(
                "select id from countries where iso_code = 'IN'", String.class));
    }

    private UUID stateId(String name) {
        return UUID.fromString(jdbc.queryForObject(
                "select id from states where country_id = ? and normalized_name = ?",
                String.class, indiaId(), normalize(name)));
    }

    private UUID cityId(String state, String name) {
        return UUID.fromString(jdbc.queryForObject(
                "select id from cities where state_id = ? and normalized_name = ?",
                String.class, stateId(state), normalize(name)));
    }

    /** Mirrors the directory's own normalization so lookups match what was imported. */
    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }

    /** Reads one stored column as text so id and name assertions stay comparable. */
    private String column(UUID userId, String column) {
        String select = switch (column) {
            case "city", "state", "country" -> column;
            case "city_id", "state_id", "country_id" -> column + "::text";
            default -> throw new IllegalArgumentException("Unexpected column: " + column);
        };
        return jdbc.queryForObject("select " + select + " from users where id = ?",
                String.class, userId);
    }

    private String userLocation(UUID userId) {
        return jdbc.queryForObject("select coalesce(country_id::text,'')||'|'"
                        + "||coalesce(state_id::text,'')||'|'||coalesce(city_id::text,'') from users where id = ?",
                String.class, userId);
    }

    private Session registerWithLocation(String name, UUID stateId, UUID cityId) {
        Session session = register(name);
        var response = api.patch("/api/v1/users/me", Map.of(
                "name", name,
                "locality", "Centre",
                "stateId", stateId.toString(),
                "cityId", cityId.toString()
        ), session.token());
        assertThat(response.status()).as("profile with location: %s", response.body()).isEqualTo(200);
        return session;
    }

    private UUID createListingAt(Session owner, String title, UUID stateId, UUID cityId, String cityText) {
        Map<String, Object> book = new HashMap<>();
        book.put("title", title);
        book.put("author", "ResourceSwap Test Author");
        book.put("publisher", "Test Press");
        book.put("classLevel", 10);
        book.put("board", "CBSE");
        book.put("subject", "Mathematics");
        book.put("edition", "2024");
        book.put("language", "English");
        var createdBook = api.post("/api/v1/catalog/books", book, owner.token());
        assertThat(createdBook.status()).as("book create: %s", createdBook.body()).isEqualTo(200);

        Map<String, Object> listing = listingPayload(UUID.fromString(createdBook.data().path("id").asString()),
                "FIXED_PRICE", cityText);
        listing.put("stateId", stateId.toString());
        listing.put("cityId", cityId.toString());
        var created = api.post("/api/v1/listings", listing, owner.token());
        assertThat(created.status()).as("listing create: %s", created.body()).isEqualTo(201);
        return UUID.fromString(created.data().path("id").asString());
    }

    private static List<String> strings(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.path(field).asString()));
        return values;
    }

    private static List<UUID> ids(JsonNode array, String field) {
        List<UUID> values = new ArrayList<>();
        array.forEach(node -> values.add(UUID.fromString(node.path(field).asString())));
        return values;
    }

    private static JsonNode named(JsonNode array, String name) {
        for (JsonNode node : array) {
            if (name.equals(node.path("name").asString())) {
                return node;
            }
        }
        throw new AssertionError("No directory row named " + name + " in " + array);
    }

    private static long countTypes(JsonNode array, String stateType) {
        return array.findValues("stateType").stream()
                .filter(node -> stateType.equals(node.asString()))
                .count();
    }
}
