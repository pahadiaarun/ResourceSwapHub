# Location data sources

Investigated 2026-01 feature work, recorded 2026-11-01; the LGD district tier of §2b was downloaded,
converted and imported on 2026-11-03. This is the provenance policy for
`countries`, `states`, `districts`, `cities` and `location_aliases` (V8).

## 1. What is authoritative for India

| Source | Publisher | Covers | Version / release used | Access | Licence / terms |
| --- | --- | --- | --- | --- | --- |
| **Local Government Directory (LGD)** | Ministry of Panchayati Raj / RNCAI, lgdirectory.gov.in | State/UT, district, sub-district (tahsil), village codes and names | LGD 2011 base with periodic state additions (the portal publishes per-state reports; some states republished in 2022-2024 after district creation) | Manual download of the published reports, or the mirrored resource on data.gov.in | Government Open Data Access Licence (GOLA) as displayed on the portal/download page |
| **data.gov.in — "Local Government Directory (LGD)" catalogue entry** | Open Government Data Platform India | Same LGD content, machine-readable (CSV/XLS) resources with a resource id | Resource-level release dates vary; the catalogue shows the last-updated field per resource | Requires a free data.gov.in API key or portal download. Programmatic scraping of the catalogue pages is not permitted and returns HTTP 403 to anonymous clients | Terms of use of data.gov.in (GOLA-style attribution, no warranty) |
| **Census 2011 Location Code Directory (LCD)** | Office of the Registrar General & Census Commissioner, censusindia.gov.in (nada catalogue id 42648) | State/UT code, district code, sub-district code, **town** or village code and name | Census of India 2011, final populated place code list | Download from the censusindia.gov.in Nada catalogue | Public government data; attribution to the Census organisation required |
| **ISO 3166-2:IN** | ISO | Five-character codes for the 28 states and 8 union territories (`IN-DL`, `IN-TS`, ...) | ISO 3166-2, current edition including the 2019/2020 changes for Jammu and Kashmir and Ladakh | Published standard (values transcribed, not scraped) | Codes are a public standard; the numbering itself is not copyrightable |
| **Secondary mirror: dataful.in dataset 21838** | Third-party mirror of the state/district/sub-district/village list | Same as LGD | Undated mirror | Public | Mirror, not a primary source — never used as the authority |

Nothing in this feature was obtained by scraping a government website. Every bulk dataset must be
downloaded by an operator who has accepted that dataset's terms, then fed through the import
pipeline below.

## 2. What the schema seeds, and why

`V8__normalized_locations.sql` seeds only what is constitutionally or standards-wise verifiable:

- Countries `IN` and `US` (a launch default and a neutral second entry — the schema is not
  India-only, and adding a country is a data change, not a migration).
- All **28 states and 8 union territories** with their ISO 3166-2:IN codes and a
  `STATE` / `UNION_TERRITORY` discriminator.
- A small set of state aliases (Delhi → "National Capital Territory of Delhi", Odisha → "Orissa",
  Tamil Nadu → "Madras State", ...).

It seeds **no cities** — a reliable, licenceable, city-level dataset is not present in this
repository, and inventing settlement rows or government codes would corrupt the directory the
feature exists to protect. Districts, by contrast, arrive from a real official export, committed as
data and loaded on boot (§2b). The directory is also **importable** by an operator:

- `LocationImportService` performs an idempotent upsert (official code first, then
  parent scope + normalized name, then name), records provenance on every row, and refuses to
  overwrite a stored official code with a different one (reported as a conflict).
- `LocationFileImportRunner` is an operator-run, offline entry point. It is not an HTTP endpoint.

```bash
java -jar app.jar \
  --bookbridge.location.import-file=/data/lgd-districts.csv \
  --bookbridge.location.import-source=LGD \
  --bookbridge.location.import-source-url=https://lgdirectory.gov.in/ \
  --bookbridge.location.import-dataset-version=2011 \
  --bookbridge.location.import-verified=true
```

`import-verified=true` should be used only for an authorized official dataset and records that a
row appears in that dataset — never that a user's own claim is verified.

### Accepted shapes

Hierarchical JSON:

```json
{
  "countryIsoCode": "IN",
  "states": [
    {
      "code": "IN-MH",
      "name": "Maharashtra",
      "type": "STATE",
      "districts": [
        { "name": "Pune", "code": "M25",
          "cities": [ { "name": "Pune", "type": "METROPOLITAN_CITY",
                        "aliases": ["Poona"] } ] }
      ]
    }
  ]
}
```

Flat CSV, one row per city, columns
`state_code,state_name,state_type,district_code,district_name,city_code,city_name,city_type,city_aliases`
(aliases pipe-separated). XLSX from a portal must be converted to CSV first; no spreadsheet
dependency is added to the build.

## 2b. The bundled LGD district export

`src/main/resources/location-seed/india-lgd-districts.json` holds **790 districts across the 36
states and union territories**, taken verbatim from the "state directory" sheet of the Local
Government Directory workbook an operator downloaded from lgdirectory.gov.in on **2026-11-03**. It is
committed data, not scraped at runtime, and it is generated by a script so the provenance is
reproducible:

```bash
python3 resourceswaphub-database/scripts/lgd_districts_to_json.py <downloaded.xlsx> \
  resourceswaphub-backend/src/main/resources/location-seed/india-lgd-districts.json
```

The converter is standard-library only and refuses to emit a file when the source is not trustworthy:
a non-numeric or duplicated LGD district code, or an LGD state name that the curated directory does
not already carry, aborts the run. State names, ISO 3166-2:IN codes and the
`STATE` / `UNION_TERRITORY` discriminator are taken from `india-curated.json` rather than from the
workbook, because LGD labels every parent row `(State)` and re-deriving the type from it would
silently downgrade the eight union territories.

Each row is imported with `official_code` = the LGD numeric district code (Mumbai is `482`), plus
`source = LGD`, `source_url = https://lgdirectory.gov.in/`, `dataset_version = 2026-11-03` and the
verified flag, which is what "this name and code appear in that authorized dataset" means here.

`LocationSeedRunner` loads it **before** the curated top-up, and only when no district row already
carries that `(source, dataset_version)` pair: an official run rewrites every row it matches, and
putting 790 such writes in front of every cold start on a remote database is not what a seed step
should do. Raising `LGD_DATASET_VERSION` after regenerating the file makes the next boot refresh.
A clean boot against an empty schema on 2026-11-03 reported `states=36 districts=790 inserted=797
updated=36 conflicts=0 failed=0`, all 790 rows coded, 36 states with at least one district, and the
634 curated city rows untouched.

## 3. The curated starter set

`src/main/resources/location-seed/india-curated.json` exists so that the cascading selects are usable
before an official import is licensed and run. Its rules:

- Only settlement names that are common knowledge (state capitals and the larger cities/towns).
- **No `official_code` on any row** — a code is only ever written when a dataset supplies it.
- District links recorded only where the relationship is stable; everything else hangs off the state
  with a null district.
- Every row is stamped `source = CURATED_SEED`, `dataset_version = null`, and an import from an
  authorized dataset updates the same rows (matched by state scope + normalized name), replacing the
  provenance stamp with the real source and adding official codes.
- Loaded by `LocationSeedRunner` on every boot as an insert-missing-only top-up, so growing the file
  adds rows without ever rewriting stored ones; disable with `bookbridge.location.seed=false`.

It is explicitly labelled as not authoritative, in the file itself and in `LOCATION_MIGRATION.md`.

## 4. Known limitations

- The 2011 vintage of LGD/Census predates district creation in Andhra Pradesh, Telangana, Jharkhand
  and Bihar since 2016-2022; those states' newer districts must come from a state republished list.
- LGD is a *rural* directory down to village level and does not enumerate statutory cities/municipalities;
  the Census town directory is the better source for `city_type = CENSUS_TOWN`, and municipal
  records for `METROPOLITAN_CITY` / `NOTIFICATION_AREA`.
- data.gov.in anonymous catalogue pages return HTTP 403; a resource id plus API key (or a manual
  download) is required. This is the documented access route, not a bug to work around.
- City-level coverage in this repository is therefore partial by design until an import runs. The
  UI handles this: the city dropdown shows an empty state and the free-text locality field stays
  available, so a user is never asked to invent a name that the directory does not hold.
- The workbook's 323 district rows each carry one local-language name, and **316 of them are lost**:
  `LocationService.normalize()` keeps only `a-z0-9`, so a Devanagari, Telugu or Tamil alias
  normalizes to the empty string and is never stored. Only the 7 Latin-script aliases survive
  (`select count(*) from location_aliases where entity_type = 'DISTRICT'` = 7). Retaining the rest
  needs a Unicode-aware normalizer, which is a change to the matching key every location row uses and
  therefore must be migration-tested, not slipped into a data import.
- `Mumbai City` (Maharashtra, curated, uncoded) still sits beside `Mumbai` (LGD code `482`): the two
  spellings do not match, so the import could not merge them. 13 of the 14 curated district rows did
  match and were re-coded. This is harmless while districts are data-only and must be resolved before
  the district tier becomes selectable, or users will be able to pick either row for the same place.
- Districts are **not yet a user-selectable field**: `users`, `listings` and `collections` have
  `country_id` / `state_id` / `city_id` but no `district_id` (V8 defines those columns). The 790 rows
  are queryable through `GET /api/v1/locations/districts?stateId=…` and available to resolve against;
  wiring them into the profile, listing and discovery paths is a separate, additive pass.

## 5. Provenance recording

`source`, `source_url` and `dataset_version` are stored on every country, state, district and city
row, alongside `created_at`/`updated_at`. That is what answers "which dataset, which version, when did
it land" for any given record. `location_aliases` carries `created_at` only, because an alias is
immutable — correcting one means inserting a new row and dropping the old.

Sources checked:
- [LGD - Local Government Directory, Government of India](https://lgdirectory.gov.in/)
- [Local Government Directory (LGD) on data.gov.in](https://www.data.gov.in/catalog/local-government-directory-lgd)
- [Census 2011 - Location Code Directory](https://censusindia.gov.in/nada/index.php/catalog/42648)
- [LGD report field guide (open data notes)](https://ramseraph.github.io/opendata/lgd/anatomy/)
- [dataful.in mirror of the state/district/sub-district/village list](https://dataful.in/datasets/21838/)
