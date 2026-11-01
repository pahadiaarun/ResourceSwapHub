# Location schema

Standardized geography for profiles, listings and collections. Created in `V8__normalized_locations.sql`,
indexed for discovery in `V9__location_discovery_indexes.sql`. Data provenance and the import
pipeline are documented in [LOCATION_DATA_SOURCES.md](LOCATION_DATA_SOURCES.md); how existing rows
were migrated is in [LOCATION_MIGRATION.md](LOCATION_MIGRATION.md).

## The invariant

A place is identified by a directory **id**, never by its name. Names are stored beside the ids only
so a page can render without an extra query, and they are always written from the directory row, so
the two can never disagree about which place a record refers to.

```text
countries ──< states ──< districts ──< cities
                 └──────< cities (district is optional)

users / listings / collections → country_id, state_id, city_id
location_aliases → (entity_type, entity_id) polymorphic spellings for any level
```

Two readers who pick the same state and city are guaranteed the same `state_id` and `city_id`,
because there is only one such row to pick: the directory is the single source, the select lists are
built from it, and the write path resolves text against it.

## Tables

### `countries`

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | UUID | no | PK |
| `iso_code` | VARCHAR(2) | no | ISO 3166-1 alpha-2, unique |
| `name` | VARCHAR(120) | no | display spelling |
| `normalized_name` | VARCHAR(120) | no | unique, search key |
| `is_active` | BOOLEAN | no | soft deactivation; inactive rows are never resolvable |
| `source` / `source_url` / `dataset_version` | VARCHAR | yes | provenance of the row |

Seeded by V8 with `IN` (India) and `US` (United States) — India is the product default, not a
database constraint.

### `states`

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | UUID | no | PK |
| `country_id` | UUID | no | FK → `countries`, `ON DELETE RESTRICT` |
| `official_code` | VARCHAR(10) | yes | ISO 3166-2:IN code (`IN-MH`); unique when present, never invented |
| `name` | VARCHAR(120) | no | |
| `normalized_name` | VARCHAR(120) | no | unique **within the country** |
| `state_type` | VARCHAR(20) | no | `STATE` or `UNION_TERRITORY` (CHECK) |
| `is_active` | BOOLEAN | no | |
| `source` / `source_url` / `dataset_version` | VARCHAR | yes | |

All 28 states and 8 union territories are inserted by V8 from the constitutional list with their
ISO 3166-2:IN codes. `ON DELETE RESTRICT` plus the scoped unique key mean a state cannot disappear
while users point at it, and a "Delhi" in another country would be a different row.

### `districts`

Same shape as `states`, keyed `UNIQUE (state_id, normalized_name)`, `official_code` reserved for
LGD codes and left NULL for curated rows.

### `cities`

| Column | Type | Null | Notes |
| --- | --- | --- | --- |
| `id` | UUID | no | PK |
| `district_id` | UUID | yes | FK → `districts`, `ON DELETE SET NULL` |
| `state_id` | UUID | no | FK → `states`, `ON DELETE RESTRICT` |
| `official_code` | VARCHAR(20) | yes | unique when present; Census/LGD code, never fabricated |
| `name` / `normalized_name` | VARCHAR(150) | no | **`UNIQUE (state_id, normalized_name)`** |
| `city_type` | VARCHAR(30) | yes | `METROPOLITAN_CITY`, `CITY`, `TOWN`, `CENSUS_TOWN`, `VILLAGE`, `NOTIFICATION_AREA`, `OTHER` |
| `is_active` | BOOLEAN | no | |
| `source` / `source_url` / `dataset_version` | VARCHAR | yes | |

`UNIQUE (state_id, normalized_name)` is the whole answer to duplicate names: Udaipur in Rajasthan
and Udaipur in Tripura are two rows with two ids, and every query that lists cities is scoped to one
state, so the name alone can never select between them.

### `location_aliases`

| Column | Type | Notes |
| --- | --- | --- |
| `entity_type` | VARCHAR(20) | `COUNTRY`, `STATE`, `DISTRICT`, `CITY` (CHECK) |
| `entity_id` | UUID | target row; intentionally not a FK because it points across four tables |
| `alias` / `normalized_alias` | VARCHAR(150) | `UNIQUE (entity_type, entity_id, normalized_alias)` |

Former and alternate spellings (`Bangalore`→Bengaluru, `Poona`→Pune, `Orissa`→Odisha,
`NCT of Delhi`→Delhi) so typed text lands on a canonical row. The import service is the only writer
and always supplies an id that exists for that entity type.

## Where records point

`users`, `listings` and `collections` each gained nullable `country_id`, `state_id`, `city_id`
(FK `ON DELETE SET NULL`) alongside the pre-existing free-text `city` / `state` / `country` columns.
The old columns are **mirrored display text**, not identity: the backfill and every write keep them
equal to the directory names, and rows the directory could not place keep what the user typed.

## Resolution rules

`LocationService.resolve(countryId, stateId, cityId, cityText, stateText, countryText)` is the only
write-side entry point, used by profile, listing, collection and backfill code.

| Input | Outcome |
| --- | --- |
| `cityId` | validated, and its own state/country win; a conflicting `stateId`/`countryId` is a `400 CITY_STATE_MISMATCH` / `CITY_COUNTRY_MISMATCH` |
| `stateId` + city text | city resolved **inside that state** (exact name → alias → unique substring); no match leaves the text as typed and `city_id` NULL |
| text only | country defaults to India when absent; a city name with no state is accepted only when exactly one row in the whole directory carries it |
| ambiguous text | nothing is guessed: ids stay NULL and the typed value is preserved |

`requireState`, `requireCity`, `requireDistrict` and `requireCountry` reject unknown or inactive
ids with `400`, so a stale client id fails loudly instead of storing a dangling reference.

## Read APIs

| Endpoint | Params | Shape |
| --- | --- | --- |
| `GET /api/v1/locations/countries` | `q` | array |
| `GET /api/v1/locations/states` | `countryId` (required), `q` | array, alphabetical by normalized name |
| `GET /api/v1/locations/districts` | `stateId` (required), `q` | array |
| `GET /api/v1/locations/cities` | `stateId` (required), `q`, `page`, `size` (max 50, default 20) | `PageResponse` — never the whole directory |
| `GET /api/v1/locations/cities/{id}` | — | one row, with `stateName` and `districtName` |

Reads are public so unauthenticated registration and search can populate the selects. Writes happen
only through the operator-run import pipeline. `q` is case- and punctuation-insensitive
(`normalize()` folds accents, strips everything outside `[a-z0-9\s]` and collapses spaces) and also
matches aliases.

## Frontend contract

`components/location-select.tsx` exports the single `LocationSelector` (plus `CountrySelect`,
`StateSelect`, `DistrictSelect`, `CitySelect`) used by the profile form, the extended profile form,
listing create/edit, collection create/edit and the discovery filters — there is no per-page
variant. Registration itself asks for no location, so the first place a user ever enters is this
selector. It emits `{countryId, stateId, cityId, …names}`, and those ids are what the URLs store;
`useLocationLabels` rehydrates the display names from a shared link by id. The city select is
disabled until a state is chosen, and its options come from the paginated per-state endpoint.

Public location surface is city + state only: no address, no coordinates, and never a
residential-level value.
