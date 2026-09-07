# Consolidate Entities

An audit of the 142 JSON Schema files in
[`3_entities_json_schemas_output`](./3_entities_json_schemas_output) (7
bounded-context folders, each with its own `schemas/`) looking for
duplicated or near-duplicated object/enum definitions, followed by
consolidating the ones worth consolidating.

The consolidated result is written to
[`4_consolidate_entities_output`](4_consolidate_entities_output) — a full copy of
`3_entities_json_schemas_output` with the changes described below applied
on top. `3_entities_json_schemas_output` itself is left exactly as step 3
produced it; every path below written as `<module>/schemas/...` refers to
that same relative path under both directories, since `4_consolidate_entities_output`
mirrors the source's structure file-for-file except for the additions and
edits this step made.

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
