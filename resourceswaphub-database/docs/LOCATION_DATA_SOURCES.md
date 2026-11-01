# Location data sources

Investigated 2026-01 feature work, recorded 2026-11-01. This is the provenance policy for
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

It deliberately seeds **no districts and no cities**. A reliable, licenceable, city-level dataset is
not present in this repository, and inventing settlement rows or government codes would corrupt the
directory the feature exists to protect. Instead the directory is **importable**:

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
- Loaded by `LocationSeedRunner` only while the `cities` table is empty; disable with
  `bookbridge.location.seed=false`.

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
