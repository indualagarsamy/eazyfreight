# Consolidate Entities

An audit of the 142 JSON Schema files in
[`3_entities_json_schemas_output`](./3_entities_json_schemas_output) (7
bounded-context folders, each with its own `schemas/`) looking for
duplicated or near-duplicated object/enum definitions, followed by
consolidating the ones worth consolidating.

The consolidated result is written to
[`4_consolidate_entities_output`](4_consolidate_entities_output) — a full
copy of `3_entities_json_schemas_output` with the changes described below
applied on top, following this project's `<N>_name.md` /
`<N>_name_output/` convention from steps 1–3. `3_entities_json_schemas_output`
itself is left exactly as step 3 produced it; every path below written as
`<module>/schemas/...` refers to that same relative path under both
directories, since `4_consolidate_entities_output` mirrors the source's
structure file-for-file except for the additions and edits this step
made.

## Method

A Python pass over all 142 files:

1. Parsed every schema; grouped the 91 `type: object` schemas and compared
   every pair's `properties` key sets by Jaccard similarity, to surface
   object shapes that repeat under different names.
2. Grouped the enum (`type: string` + `enum`) schemas by exact value-set
   equality, then again by partial overlap, to surface repeated or
   drifted enums.
3. Grepped for the `amount`/`currency` property pair specifically, since
   a "money" value object is a common source of copy-paste in this kind
   of domain.
4. Extracted every `$ref` graph edge to confirm whether any schema
   references a sibling in a *different* bounded-context folder (it
   doesn't — all 108 internal `$ref`s stay inside their own module,
   confirming the 7 folders are deliberately isolated bounded contexts,
   not an oversight).

## Findings

### Cross-context duplicates — intentional, left alone

Bounded contexts in this project don't `$ref` across folders, so each
context that needs a concept owned by another duplicates it locally. This
was already called out explicitly in step 2's method notes and in one of
the schemas themselves, so these are not treated as bugs:

- **`ContainerType`** — `booking/` and `logistics/`, byte-identical
  4-value enum. `logistics/schemas/ContainerType.schema.json` carries a
  comment: *"Shared with the booking context
  (com.eazyfreight.booking.domain.ContainerType); duplicated here so this
  spec stands alone."*
- **`ShippingMode`** — `booking/` (`OCEAN_FCL`, `OCEAN_LCL`) is a subset
  of `quote/`'s (adds `AIR`), with a description on booking's copy
  explaining it's ocean-only. A hand-synced subset, not a copy error.

Not touched by this step.

### Cross-context near-duplicate object shapes — consolidated

- **Cargo detail, modeled 4 times.** `booking/BookingCargoDetailRequest`,
  `booking/BookingCargoDetailResponse`, `quote/CargoDetailRequest`,
  `quote/CargoDetailResponse` all describe the same physical cargo shape
  (`hazmat`, `hsCode`, `heightCm`/`widthCm`/`lengthCm`, `weightKg`,
  `pieces`, `oversized`, `temperatureControlled`, `description`) —
  60–86% property overlap pairwise — each with its own context-specific
  extras (booking: `marksAndNumbers`, `valueUsd`, `cbm`; quote:
  `chargeableWeight`, `volumetricWeightKg`/`Cbm`, `chargeableUnit`).

### Repeated inline `currency`/`amount` fields — consolidated

Re-reading all 14 files that had a `currency` and/or `amount` field
(rather than just diffing their property-name sets) showed the original
framing — "a `Money {amount, currency}` object duplicated 14 times" — was
too strong. Most of these objects have a `currency` field governing
*several* differently-named amount fields (`InvoiceView`: one `currency`
for `totalAmount`, `totalBuyAmount`, `paidAmount`, `outstandingAmount`;
`QuoteLineResponse`/`RateResponse`: one `currency` for `buyRate`/
`sellRate`/`amount`). Wrapping those in a nested `Money` object would
mean restructuring the DTO shape, not deduplicating a definition. Only 4
schemas (`PayableView`, `PaymentView`, `StorageFeeView`,
`SurchargeResponse`) actually have `amount` and `currency` as a natural
pair.

What *is* genuinely duplicated, independent of pairing, is the **field-level
type declaration**: the literal `"currency": {"type": "string", ...}` and
`"amount": {"type": "number", ...}` blocks, repeated inline everywhere
they occur, and already drifted:

- 11 `currency` fields, mostly a bare `{"type": "string"}`, except
  `CreateQuoteRequest` (ISO-4217 `pattern`) and `QuoteResponse`
  (nullable).
- 8 `amount` fields, mostly an unconstrained `number`, except
  `CreditNote`/`RecordPayment` (`minimum: 0.01`).

Consolidated at the field-type level (see "What we did" below) rather
than by restructuring any object's shape.

### Reviewed and reversed: `InvoiceLineView` vs `Line`

The original pass (see step 4 planning notes) flagged `finance/Line` and
`finance/InvoiceLineView` as same-context copy-paste and planned to merge
them. Re-reading them side by side against the *other* request/response
pairs in this audit showed that's wrong: `Line` (used by `UpdateLines`)
holds exactly the writable fields (`description`, `buyAmount`,
`sellAmount`, `quantity`, `unit`), and `InvoiceLineView` adds the
computed/read-only fields on top (`id`, `lineNumber`, `extendedBuy`,
`extendedSell`, `margin`) — the identical shape of the
`QuoteLineRequest`/`QuoteLineResponse` and `PaymentView`/`RecordPayment`
pairs that were correctly left alone below. There's no principled reason
to merge this pair and not those. **Not touched.**

### Left as-is — reviewed, no action

These looked similar on the automated pass but turned out to be
legitimate distinct concepts, or a normal request/response pairing not
worth collapsing:

- `QuoteLineType` vs `SurchargeType` (`quote/`) — `SurchargeType`'s 6
  values are a subset of `QuoteLineType`'s 8 (missing `BASE_FREIGHT`,
  `INSURANCE`). Kept separate: `QuoteLineType` classifies a quote line
  (including the non-surcharge base freight line), `SurchargeType`
  classifies a rate surcharge — different vocabularies that happen to
  share most tokens.
- `Distribute` vs `Distribution` (`documentation/`) — command shape vs.
  persisted record (adds `id`, `sentAt`, `sentBy`, `revisionNumber`,
  nullable fields). Normal command/view pairing.
- `QuoteLineRequest` vs `QuoteLineResponse`, `PaymentView` vs
  `RecordPayment`, `Line` vs `InvoiceLineView` — same pattern,
  response/view adds `id` + server-computed fields over the request
  shape. Left alone; that's the request/response DTO convention
  throughout this project, not duplication.
- `AlertHistoryAction` vs `AlertStatus` (alerts), `DeliveryStatus`
  (alerts) vs `DispatchStatus` (logistics), `HouseBOLStatus`
  (documentation) vs `InvoiceStatus` (finance) — coincidental token
  overlap between unrelated lifecycles, not the same concept.

## What we did

Within each affected bounded-context's `schemas/` folder, added a
`common/` subfolder holding new shared definitions, referenced only by
local, same-context `$ref` (a bare filename or `common/<Name>.schema.json`)
— never across a context boundary, preserving the isolation the rest of
the project already relies on. Nothing was shared *between* `booking/`
and `quote/`, or between `finance/` and `quote/` — where both sides of a
boundary needed the same shared def, it was written twice (once per
context's `common/`), the same way `ContainerType` already is, each copy
carrying a comment pointing at its sibling and at this doc.

1. **Cargo detail base shapes.** Added `booking/schemas/common/
   CargoDimensionsInput.schema.json` and `.../CargoDimensionsView.schema.json`
   (mirrored into `quote/schemas/common/`), covering the fields and
   constraints shared by all four cargo-detail schemas identified above.
   - `booking/BookingCargoDetailRequest` and `quote/CargoDetailRequest`
     now compose `CargoDimensionsInput` (the latter is now just a bare
     `$ref` — it added nothing beyond the shared fields).
   - `booking/BookingCargoDetailResponse` and `quote/CargoDetailResponse`
     now compose `CargoDimensionsView` plus their own extra
     response-only fields (`id`, `cbm`/`valueUsd`/`marksAndNumbers` for
     booking; `id`, `volumetricWeightKg`/`Cbm`, `chargeableWeight`,
     `chargeableUnit` for quote), via `allOf`.
   - Every field and constraint (`hsCode` pattern, dimension minimums,
     `pieces` minimum, required lists) is preserved exactly — this only
     moved where each is declared, verified below.

2. **`Currency`/`MonetaryAmount` field-level defs.** Added
   `finance/schemas/common/Currency.schema.json` and
   `.../MonetaryAmount.schema.json` (mirrored into `quote/schemas/common/`).
   `Currency` adopts the ISO-4217 `pattern` that previously only existed
   on `quote/CreateQuoteRequest`; `MonetaryAmount` is `type: number,
   minimum: 0` (rejecting negative amounts, previously unconstrained
   everywhere except `CreditNote`/`RecordPayment`). Re-pointed every
   `currency` field (11 occurrences: `CalculateStorageFee`, `InvoiceView`,
   `PayableView`, `PaymentView`, `PrepareInvoice`, `StorageFeeView`,
   `CreateQuoteRequest`, `QuoteLineResponse`, `QuoteResponse`,
   `RateResponse`, `SurchargeResponse`) and every literal `amount` field
   (8 occurrences: `CarrierInvoice`, `CreditNote`, `PayableView`,
   `PaymentView`, `RecordPayment`, `StorageFeeView`, `QuoteLineResponse`,
   `SurchargeResponse`) at the corresponding shared def via `$ref`.
   - `CreditNote`/`RecordPayment` keep their stricter `minimum: 0.01` as
     a sibling of `$ref` (2020-12 applies both; the tighter one wins) —
     the shared def wasn't loosened to accommodate them.
   - `QuoteResponse.currency` stays nullable via `anyOf: [{$ref:
     Currency}, {type: null}]`, since a `$ref`'d schema's own `type`
     can't be widened by a sibling `type` in 2020-12 (both must hold).
3. Left every "left as-is" item, including `Line`/`InvoiceLineView`,
   untouched.
4. **Bumped `info.version`** in `4_consolidate_entities_output/finance/finance.yaml`
   and `.../quote/quote.yaml` from `1.0` to `1.1` — the two specs whose
   schemas, once `$ref`s are resolved, now validate differently than
   before (see "Effective validation changes" below). No other spec's
   resolved schemas changed, so no other spec's version moved; the yaml
   files themselves are otherwise still byte-identical to step 3's
   output, this `info.version` line is the only literal edit to a spec
   file this step made.

## Effective validation changes (after resolving the new `$ref`s)

None of the yaml specs' own content changed (confirmed above), and no
schema's property names, `required` lists, or structure changed either —
but resolving the new `common/` `$ref`s shows a handful of fields now
validate more strictly than they did in `3_entities_json_schemas_output`,
because the shared def adopted the *strictest* pre-existing constraint
rather than the loosest one (by design — see "Repeated inline `currency`/
`amount` fields" above):

- **9 `currency` fields** went from a bare, unconstrained string to
  requiring the ISO-4217 `[A-Z]{3}` pattern: `finance/CalculateStorageFee`,
  `finance/InvoiceView`, `finance/PayableView`, `finance/PaymentView`,
  `finance/PrepareInvoice`, `finance/StorageFeeView`,
  `quote/QuoteLineResponse`, `quote/RateResponse`,
  `quote/SurchargeResponse` — plus `quote/QuoteResponse.currency`, which
  keeps its nullability but now requires the pattern on the non-null
  branch.
- **6 `amount` fields** went from an unconstrained `number` to
  `minimum: 0` (rejecting negative amounts): `finance/PayableView`,
  `finance/PaymentView`, `finance/StorageFeeView`,
  `finance/CarrierInvoice`, `quote/QuoteLineResponse`,
  `quote/SurchargeResponse`.
- Unchanged: `quote/CreateQuoteRequest.currency` (already had the ISO
  pattern), `finance/CreditNote.amount`/`finance/RecordPayment.amount`
  (already required `minimum: 0.01`, stricter than the shared def's
  `minimum: 0` and preserved as a sibling constraint), and every cargo
  detail field in `booking/` and `quote/` (same fields, same
  `required`/`minimum`/`pattern` — confirmed identical by instance
  validation above, just composed via `allOf` instead of repeated
  inline).

This only affects `finance/` and `quote/` schemas, hence only those two
specs' version bump above.

## Verification

- All 150 schema files (142 original + 8 new `common/` defs) parse and
  pass `Draft202012Validator.check_schema`.
- All 131 `$ref`s (up from 108 — the new composed schemas add more)
  resolve to an existing file in the *same* bounded-context folder; zero
  cross-context refs, confirmed by walking every `$ref` edge and
  comparing source/target top-level folder.
- Instance-validated the reworked schemas with `jsonschema` +
  `referencing.Registry` (so cross-file `$ref`s resolve the way a real
  consumer's validator would), both positive and negative cases:
  - `BookingCargoDetailRequest`/`CargoDetailRequest`: a valid cargo
    instance passes; dropping a base-schema-required field (`hsCode`)
    fails; dropping the wrapper-only required field (`valueUsd`) fails
    — confirming `allOf` composition still enforces both layers'
    `required` lists.
  - `BookingCargoDetailResponse`/`CargoDetailResponse`: valid instances
    with each side's extra fields pass.
  - `PaymentView`: `currency: "USD"` passes, `"usd"` now correctly fails
    the ISO pattern, `amount: -5` now correctly fails the new
    non-negative minimum.
  - `CreditNote`: `amount: 0.01` passes, `amount: 0` fails — the
    schema-specific stricter minimum survived moving to a `$ref`.
  - `QuoteResponse.currency`: `null` passes, `"EUR"` passes, `"invalid"`
    fails — nullability preserved alongside the shared pattern.
  - `CreateQuoteRequest.currency`: `"usd"` still fails the ISO pattern
    through the new `$ref` (unchanged behavior, just relocated).

## Totals

| | Count |
|---|---|
| Schema files audited (in `3_entities_json_schemas_output`) | 142 |
| New shared `common/` defs added | 8 (2 cargo-dimension defs × 2 contexts + 2 currency/amount defs × 2 contexts) |
| Existing schemas edited to `$ref` a shared def | 17 |
| Schema files in `4_consolidate_entities_output` | 150 |
| `$ref`s in the consolidated output (up from 108 in the source) | 131 |
| Cross-context `$ref`s introduced | 0 |

Committed across three commits on the `4_consolidate_entities` branch:

- `3dd06d4` — *"Consolidate duplicated entity JSON schemas into
  4_consolidate_entities_output"* — added `4_consolidate_entities.md` and
  all 150 files of `4_consolidate_entities_output`, with
  `3_entities_json_schemas_output` left untouched.
- `3f78138` — *"Document totals and commit reference in consolidation
  doc"* — rounded out this doc with the Totals table.
- `b7b376a` — *"Bump finance/quote spec versions for the tightened field
  validation"* — the `info.version` `1.0` → `1.1` bump on
  `finance/finance.yaml` and `quote/quote.yaml`, plus the "Effective
  validation changes" section above.
