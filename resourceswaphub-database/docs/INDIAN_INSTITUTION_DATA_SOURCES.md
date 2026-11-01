# Indian Institution Data Sources

This document records the **real, currently-accessible** authoritative sources for Indian
educational-institution data that could feed the ResourceSwapHub institution directory, plus an
honest assessment of how each can (and cannot) be obtained programmatically.

## Ground rules (from the implementation brief)

- **Never fabricate** institution records, official identifiers, API endpoints, or dataset
  availability. Everything below was checked against the live source site on 2026-11-01.
- **Do not bypass** CAPTCHA, authentication, rate limits, or access controls. **No unauthorized
  scraping.** Where a source only offers a human-facing portal, we treat it as *manual-download*
  (an operator fetches the published file, then runs the import pipeline) — not something to
  crawl automatically.
- "Verified" in our schema means *"this record appears in an authoritative government dataset."*
  It **never** means a user's affiliation with an institution has been verified. Seeded/curated
  rows therefore stay `verification_status = PENDING`.
- Licensing must be respected per source. NDSAP-published open data is shareable; portal terms
  and any per-dataset licence govern reuse and must be read before redistribution.

## Source assessment

| Source | Covers | What's actually available | Programmatic access | Licence / terms | Recommended use |
|---|---|---|---|---|---|
| **AISHE** — All India Survey on Higher Education, MoE ([aishe.gov.in](https://aishe.gov.in/)) | Universities, colleges, standalone HE institutions | Final reports per year; the 2023-24 report page publishes a **downloadable report (PDF and an Excel/aggregate file)**. Institution-level counts (e.g. 1,289 universities, 48,246 colleges, 15,221 standalone for 2023-24). | **Manual download** of the published report files. No open public REST API for the full institution list was found; the portal itself is login-gated for institutions submitting data. | Government of India publication; reuse per portal terms — read before redistribution. | Higher-education institution names/locations as a **manually downloaded** dataset, imported via the pipeline. |
| **UDISE+** — Unified District Information System for Education Plus ([udiseplus.gov.in](https://udiseplus.gov.in/), Know Your School: [kys.udiseplus.gov.in](https://kys.udiseplus.gov.in/)) | Schools (primary/secondary), ~14.9 lakh schools | "Know Your School" public lookup + a dashboard; report/data downloads published on the site and via the KYS app. School directory management is login-gated. | **Manual download / lookup.** No documented open bulk API found; KYS is a search UI/app, not an open data endpoint. Do not crawl it. | Government of India; per portal/app terms. | School names + UDISE codes as a **manually downloaded** dataset for the SCHOOL type. |
| **data.gov.in** — Open Government Data Platform ([data.gov.in](https://data.gov.in/)) | Aggregator of many ministries' open datasets | Datasets/resources published under **NDSAP**; resources exposed through the OGD resource API at `https://api.data.gov.in/resource/{resource_id}` with a **free registered API key** (register on the portal). | **Yes — official REST API** with an API key, paginated, documented by the platform. This is the legitimate programmatic path when a relevant education dataset is published there. | NDSAP 2012 open-data policy; per-dataset terms shown on each resource page. | Preferred route **if** a suitable institution/education dataset is published as a resource. Search the catalog first; only ingest datasets that actually exist. |
| **AICTE** — All India Council for Technical Education ([aicte.gov.in](https://www.aicte.gov.in/), [aicte-india.org](https://www.aicte-india.org/)) | Technical/approved institutions (engineering, management, etc.) | Public "AICTE Approved Universities/Institutions" list pages and **Extension of Approval (EoA)** letters/reports per academic year (PDF/portal). | **Manual download** of published approval lists/EoA reports. No open bulk REST API found. | Government of India; per portal terms. | Technical-institution names + approval year, as a manually downloaded dataset. Use only to record "listed in AICTE approvals", not to assert quality. |
| **UGC** — University Grants Commission ([ugc.gov.in](https://www.ugc.gov.in/)) | Recognised universities by category (Central, State, Deemed, Private) | Public university-detail listing pages and **consolidated PDF lists** of all universities (state-wise, private, etc.). | **Manual download** of the consolidated lists. The listing pages are a human-facing UI (obfuscated query strings); do not scrape. | Government of India; per site terms. | Authoritative university names + category, as a manually downloaded dataset for the UNIVERSITY type. |
| **NIRF** — National Institutional Ranking Framework ([nirfindia.org](https://www.nirfindia.org/)) | Ranked institutions across categories (Overall, University, College, Engineering, Management, Medical, etc.) | Public **ranking HTML pages** and a **ranking report PDF**; each ranked institution has a NIRF ID (e.g. `IR-O-U-0120`). Data submission (DCS/login) is institution-gated. | **Manual download** of the published report/pages. No open bulk API found; the DCS login portal is not for public data extraction. | Government of India publication; per site terms. | Ranking/name/NIRF-ID reference for well-known institutions. Do **not** present rankings as endorsements inside ResourceSwapHub without clear labelling. |

## What this means for implementation

1. **There is no single, open, legally-scrapable bulk API** that returns *every* Indian
   institution with official codes. The realistic, compliant pipeline is:
   - Use **data.gov.in's official resource API** (with a registered key) *only* for datasets that
     genuinely exist there and are published as resources.
   - For AISHE / UDISE+ / AICTE / UGC / NIRF, an **operator manually downloads** the published
     report/list, converts it to CSV or JSON, and runs the import (see
     [INSTITUTION_IMPORT_GUIDE.md](./INSTITUTION_IMPORT_GUIDE.md)).
2. **Official identifiers are only set when they come from the authoritative source.** We do not
   invent UDISE codes, AISHE IDs, AICTE EoA numbers, or NIRF IDs. Rows lacking a code stay
   `official_code = NULL`, `official_code_type = NULL`.
3. **Provenance is always recorded**: `source`, `source_url`, `source_last_updated`, and
   `verification_status` (`PENDING` for curated/seed, `VERIFIED` only when loaded from an
   authoritative dataset by an operator with `import-verified=true`).

## Curated seed (what ships in the repo today)

The application ships a **small curated seed** (`institution-seed/institutions.json`, ~24
well-known institutions: IITs, DTU, University of Delhi, AIIMS Delhi, NITs, BITS Pilani, VIT,
IISc, BHU, AMU, etc.) so search works out of the box. These use **real public names, cities/states
and common abbreviations/former names as aliases**, but carry:

- `official_code = NULL` (no fabricated identifiers),
- `source = CURATED_SEED`,
- `verification_status = PENDING`.

The seed is **not** a substitute for authoritative data; it is a demo/bootstrap set. Replacing or
augmenting it with AISHE/UDISE+/UGC/AICTE-derived data is a documented operator task.

## Honest limitations & follow-ups

- Access details (file formats, whether a bulk institution-level file is downloadable vs. only
  aggregate report tables) change year to year; **re-verify each source before an ingestion run.**
- We have **not** downloaded or redistributed any of these datasets in this repo. Only public
  names/locations for a small curated seed are included, without official codes.
- Any future automated pull should go **only** through data.gov.in's key-authenticated resource API
  (respecting rate limits) or operator-downloaded files — never by scraping login-gated portals or
  bypassing CAPTCHA/controls.

## Sources consulted (2026-11-01)

- [AISHE — Final Report](https://aishe.gov.in/aishe-final-report/) · [AISHE home](https://aishe.gov.in/)
- [UDISE+](https://udiseplus.gov.in/) · [Know Your School](https://kys.udiseplus.gov.in/) · [UDISE+ Dashboard](https://dashboard.udiseplus.gov.in/)
- [data.gov.in](https://data.gov.in/) · OGD resource API pattern `https://api.data.gov.in/resource/{resource_id}` · [NDSAP 2012](https://data.gov.in/sites/default/files/NDSAP.pdf)
- [AICTE](https://www.aicte.gov.in/) · [AICTE approved universities](https://www.aicte-india.org/education/institutions/Universities) · [AICTE EoA download info](https://www.aicte.gov.in/content/downloading-letter-extension-approval-eoa-existing-institutes-letter-approval-loa-new)
- [UGC](https://www.ugc.gov.in/) · [UGC consolidated list of all universities (PDF)](https://www.ugc.gov.in/oldpdf/Consolidated%20list%20of%20All%20Universities.pdf)
- [NIRF](https://www.nirfindia.org/) · [India Rankings 2025](https://www.nirfindia.org/Rankings/2025/Ranking.html) · [IR2025 Report (PDF)](https://www.nirfindia.org/nirfpdfcdn/2025/pdf/Report/IR2025_Report.pdf)
