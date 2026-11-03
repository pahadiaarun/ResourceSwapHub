#!/usr/bin/env python3
"""
Convert the Government of India LGD (Local Government Directory) state/district workbook into
the location import format this API already understands.

    python3 resourceswaphub-database/scripts/lgd_districts_to_json.py \
        "LGD - Local Government Directory, Government of India.xlsx" \
        resourceswaphub-backend/src/main/resources/location-seed/india-lgd-districts.json

The workbook is district-level: one row per district, with the LGD district code, an English name,
a local-language name and a short code. It carries no towns or cities.

State names, ISO 3166-2 codes and STATE/UNION_TERRITORY types come from the curated seed file, not
from the workbook: LGD labels every row "(State)", including the eight union territories, so
reading the type from the file would downgrade them. A district row whose state is not in the
curated list aborts the conversion rather than silently creating a new state record.
"""

import json
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET
import zipfile

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
COLUMNS = {
    "state_code": "B",
    "state_name": "C",
    "district_lgd_code": "D",
    "district_en": "E",
    "district_local": "F",
    "district_short": "H",
    "census_2011": "J",
}


def normalize(value):
    """Mirrors LocationService.normalize: lower-case, alphanumerics and single spaces."""
    lowered = unicodedata.normalize("NFKD", (value or "").lower())
    return " ".join(re.sub(r"[^a-z0-9]+", " ", lowered).split())


def lgd_state_name(raw):
    name = re.sub(r"\s*\((State|UT|Union Territory)\)\s*$", "", raw, flags=re.I).strip()
    return name[4:].strip() if name.startswith("The ") else name


def read_rows(path):
    with zipfile.ZipFile(path) as archive:
        sheet = ET.fromstring(archive.read("xl/worksheets/sheet1.xml"))
    rows = []
    for element in sheet.iter(NS + "row"):
        cells = {}
        for cell in element.iter(NS + "c"):
            column = re.match(r"[A-Z]+", cell.get("r")).group()
            if cell.get("t") == "inlineStr":
                cells[column] = "".join(node.text or "" for node in cell.iter(NS + "t")).strip()
            else:
                value = cell.find(NS + "v")
                cells[column] = (value.text or "").strip() if value is not None and value.text else ""
        rows.append(cells)
    if not rows:
        raise SystemExit("workbook has no rows")
    return [row for row in rows[1:] if row.get(COLUMNS["district_en"])]


def read_states(curated_path):
    with open(curated_path, encoding="utf-8") as handle:
        curated = json.load(handle)
    by_name = {}
    for state in curated["states"]:
        by_name[normalize(state["name"])] = {
            "name": state["name"],
            "code": state["code"],
            "type": state["type"],
        }
    return curated["countryIsoCode"], by_name


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    workbook, output = sys.argv[1], sys.argv[2]
    curated = "resourceswaphub-backend/src/main/resources/location-seed/india-curated.json"

    country_iso, known_states = read_states(curated)
    rows = read_rows(workbook)

    districts_by_state = {}
    seen_codes = {}
    for row in rows:
        state = lgd_state_name(row[COLUMNS["state_name"]])
        name = row[COLUMNS["district_en"]]
        code = row[COLUMNS["district_lgd_code"]]
        if not code.isdigit():
            raise SystemExit(f"{state} / {name}: LGD code '{code}' is not numeric")
        if code in seen_codes:
            raise SystemExit(f"duplicate LGD code {code}: '{name}' and '{seen_codes[code]}'")
        seen_codes[code] = name
        districts_by_state.setdefault(state, []).append((name, code, row[COLUMNS["district_local"]]))

    states = []
    for state, districts in districts_by_state.items():
        known = known_states.get(normalize(state))
        if known is None:
            raise SystemExit(f"LGD state '{state}' has no record in {curated}; refusing to invent one")
        entries = []
        for name, code, local in sorted(districts, key=lambda item: normalize(item[0])):
            aliases = [local] if local and normalize(local) != normalize(name) else []
            entries.append({"name": name, "code": code, "aliases": aliases})
        states.append({
            "code": known["code"],
            "name": known["name"],
            "type": known["type"],
            "districts": entries,
        })
    states.sort(key=lambda item: normalize(item["name"]))

    payload = {
        "_comment": ("Derived from the Government of India Local Government Directory (LGD) state "
                     "directory export, 790 districts across 36 states and union territories. "
                     "District names and codes are verbatim from that export; local-language names "
                     "are kept as aliases. State names, ISO 3166-2 codes and STATE/UNION_TERRITORY "
                     "types come from india-curated.json because LGD labels every row '(State)'. "
                     "Regenerate with scripts/lgd_districts_to_json.py."),
        "countryIsoCode": country_iso,
        "states": states,
    }
    with open(output, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, ensure_ascii=False, indent=1)
        handle.write("\n")

    total = sum(len(state["districts"]) for state in states)
    print(f"wrote {output}: {len(states)} states, {total} districts")


if __name__ == "__main__":
    main()
