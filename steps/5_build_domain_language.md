# Build Domain Language

A ubiquitous-language glossary derived from the 150 JSON Schema files
carried over from
[`4_consolidate_entities_output`](4_consolidate_entities.md), one entry
per schema, organized by the same 7 bounded-context folders
(`alerts`, `booking`, `compliance`, `documentation`, `finance`,
`logistics`, `quote`) rather than by schema file name — the goal is a
document a domain expert or new engineer can read top to bottom per
context to learn what each concept *means*, not just what fields it has.

Written to
[`5_build_domain_language_output/domain_language.md`](5_build_domain_language_output/domain_language.md).
The `5_build_domain_language_output/` folder itself is a straight copy of
`4_consolidate_entities_output` (all 150 schema files plus the 7 spec
`.yaml` files, unchanged) — this step reads that copy but doesn't modify
any schema or spec; `domain_language.md` is the only new artifact.

## Method

1. Concatenated every `*.schema.json` file per module (via `find`/`cat`,
   not by hand — 150 files is too much to open individually) and read
   each module's full set in one pass, together with the module's
   `.yaml` spec where a schema's purpose wasn't obvious from its fields
   alone.
2. For each module, classified every schema into one of four roles based
   on its shape and how other schemas reference it, rather than treating
   the flat file list as the organizing principle:
   - **Core entity/aggregate** — the root object the context revolves
     around (e.g. `BookingResponse`, `Logistics`, `EEIFilingResponse`).
   - **Supporting entities / value objects** — objects composed into the
     core entity or returned alongside it (e.g. `Dispatch`, `Seal`,
     `CarrierBookingResponse`).
   - **Enumerations** — `type: string` + `enum` schemas, written up as a
     table of value → business meaning rather than a bare value list,
     using each enum's `description` field where present and inferring
     meaning from usage (which entity references it, what the values
     look like) where absent.
   - **Commands** — imperative-named request schemas (`RecordSeal`,
     `CancelBookingRequest`, `Resolve`) that represent a state-changing
     operation, kept separate from response/view schemas even when they
     share most of their fields.
3. Cross-checked every `$ref` while reading, so the write-up reflects the
   actual composition graph (e.g. `BookingCargoDetailResponse` documented
   as "`CargoDimensionsView` plus booking-specific fields," not as a
   flat, independent field list).
4. Pulled forward the shared-duplication story from
   [`4_consolidate_entities.md`](4_consolidate_entities.md) into its own
   "Shared Concepts" section up top, so `ContainerType`, `ShippingMode`,
   `Currency`, `MonetaryAmount`, and the two `CargoDimensions*` schemas
   are explained once instead of twice (once per context they appear
   in), with a pointer back to that doc for why each is duplicated
   rather than `$ref`'d across the context boundary.

## Coverage

Every one of the 150 schema files in `5_build_domain_language_output/` has an
entry in `domain_language.md`; none were skipped as "self-explanatory."
The 8 shared `common/` defs (`ContainerType` ×2, `ShippingMode` ×2,
`Currency` ×2, `MonetaryAmount` ×2, `CargoDimensionsInput`/`View` ×2 each)
are documented once each in the shared section rather than once per
occurrence, so the doc has 142 distinct concept write-ups covering all
150 files.

| Module | Schema files | Core entity/aggregate | Enums documented | Commands documented |
|---|---|---|---|---|
| alerts | 18 | `AlertView` | 8 | 3 |
| booking | 19 | `BookingResponse` | 6 | 7 |
| compliance | 13 | `EEIFilingResponse` | 3 | 7 |
| documentation | 24 | `Instructions`, `Master`, `House` | 7 | 9 |
| finance | 24 | `InvoiceView`, `PayableView`, `StorageFeeView`, `CreditHoldView` | 7 | 8 |
| logistics | 26 | `Logistics` | 10 | 9 |
| quote | 18 | `QuoteResponse` | 7 | 4 |

Three modules (`documentation`, `finance`) surface more than one core
entity rather than a single aggregate root, because those contexts each
own several independently-lifecycled documents/records (a House BOL, a
Master BOL, and Instructions are three separate things with three
separate lifecycles, not one aggregate with sub-objects) — called out
explicitly in the glossary rather than forced into a single "the entity"
framing.

## Verification

- Every schema file's `title` appears at least once in
  `domain_language.md` (either under its own heading or folded into a
  parent entity's description, e.g. `AlertHistoryAction`'s values are
  covered inside the `HistoryEntry`/`AlertView` write-up as well as its
  own enum-table row).
- Every enum's value list in the glossary matches its schema file
  exactly — no values added, dropped, or reworded.
- The "Shared Concepts" section's claims about which pairs are
  byte-identical vs. deliberately divergent (`ContainerType`;
  `ShippingMode` a hand-synced subset) were re-verified against the
  actual files with `diff`, not assumed from
  [`4_consolidate_entities.md`](4_consolidate_entities.md)'s prior
  write-up.

### Re-check after merging `4_consolidate_entities` (2026-09-15)

Branch `5_build_domain_language` merged in `4_consolidate_entities` (which
had itself merged `3_entities_json_schemas`), so the glossary was re-run
through steps 1-5 to confirm nothing in the schema set moved underneath it.
Findings:

- The merge itself touched only `steps/3_entities_json_schemas_output/*.yaml`,
  three new unrelated skills, `.gitignore`, and `BookingApiMapper.java` — it
  did not add, remove, or edit any file under
  `steps/4_consolidate_entities_output/` or
  `steps/5_build_domain_language_output/`. Schema file count is still 150
  (confirmed by recount, not by trusting the prior total), and
  `5_build_domain_language_output/` is still byte-for-byte identical to
  `4_consolidate_entities_output/` apart from `domain_language.md`.
- Re-running the title-coverage check (every schema `title` must appear in
  `domain_language.md`) surfaced one pre-existing gap the original pass
  missed: `ItnGateStatus` (a fixed-shape `GET
  /bookings/{bookingId}/itn-gate` response, same pattern as
  `FilingSystemStatus` in `compliance`) was described conceptually inside
  the `Logistics` write-up but its schema title never appeared verbatim, so
  it had no table row. Added it to the Logistics enumerations table as a
  "(fixed-shape response, not an enum)" row, matching `FilingSystemStatus`'s
  treatment — bumping logistics enums documented from 9 to 10 and the
  Enumerations-documented total from 47 to 48. Pre-existing since `5ea60b6`,
  not introduced by this merge.
- Re-running the shared-pair `diff` also caught that the "Shared Concepts"
  section's `ContainerType` claim ("byte-identical in both contexts") was
  never quite true: `logistics/schemas/ContainerType.schema.json` carries an
  extra `description` field pointing back at `booking`'s copy that
  `booking`'s own file doesn't have. The four enum values are identical;
  only the description differs. Reworded the claim accordingly. Also
  pre-existing since `5ea60b6`.
- `ShippingMode`'s claim (quote: 3 modes, booking: ocean-only subset) still
  checked out exactly against the current files, no change needed.

## Totals

| | Count |
|---|---|
| Schema files covered (in `5_build_domain_language_output/`) | 150 |
| Bounded contexts | 7 |
| Distinct concept write-ups (150 files minus 8 shared duplicates counted once) | 142 |
| Enumerations documented | 48 |
| Command schemas documented | 47 |

## Commits

- `5bef265` — *"5 preview"* — set up this step: copied
  `4_consolidate_entities_output` file-for-file into
  `5_build_domain_language_output/` and added the placeholder
  `5_build_domain_language.md`.
- `5ea60b6` — *"Build domain language glossary from consolidated entity
  schemas"* — wrote this doc and `domain_language.md` (under the
  then-current `5_build_domain_language/` folder name).
- `63c4ad7` — *"renamed folder"* — renamed
  `5_build_domain_language/` to `5_build_domain_language_output/`,
  updating every path reference in this doc to match.
