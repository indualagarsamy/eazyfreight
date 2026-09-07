# Arazzo Workflow

An [Arazzo](https://spec.openapis.org/arazzo/latest.html) 1.1.0 document
that chains three endpoints — one each from the `quote`, `booking`, and
`logistics` OpenAPI specs — into a single "new shipment" business process:
price it, book it, dispatch the truck for it. None of those three specs
`$ref` each other (each bounded context duplicates the concepts it needs
rather than reaching across the boundary — see
[`5_build_domain_language.md`](5_build_domain_language.md)); Arazzo is the
first place in this project where the cross-context call sequence a real
operator follows is written down as an executable artifact instead of
prose.

Written to
[`6_arazzo_workflow_output/arazzo.yaml`](6_arazzo_workflow_output/arazzo.yaml).
The `6_arazzo_workflow_output/` folder is otherwise a straight copy of
[`5_build_domain_language_output`](5_build_domain_language_output) (all
150 schema files and 7 spec `.yaml` files, unchanged; the glossary
`domain_language.md` wasn't carried forward since this step consumes the
specs, not the write-up about them) — `arazzo.yaml` is the only new file.

## Why these three endpoints

Looked for the shortest chain, one step per context, where each step's
output is actually consumed by the next — not just three unrelated
endpoints glued together:

1. **`quoteApi.createQuote`** (`POST /api/quotes`) — prices a shipment for
   a customer and lane.
2. **`bookingApi.createBooking`** (`POST /api/bookings`) — converts that
   quote into a confirmed booking. Reuses the quote's `customerId`,
   `shippingMode`, `originPortCode`, `destinationPortCode`, and
   `incoterms` via `$steps.createQuote.outputs.*`, plus passes the
   `quoteId` itself so the booking records which quote it came from.
3. **`logisticsApi.dispatchOutbound`** (`POST
   /api/logistics/bookings/{bookingId}/outbound-dispatch`) — dispatches
   the truck that collects the cargo, addressed by
   `$steps.createBooking.outputs.bookingId`.

`acceptQuote`/`sendQuote` were considered instead of a bare `createQuote`,
but they need a quote to already exist and add a step without adding a
new source spec — `createQuote` alone gets a self-contained workflow with
exactly one step per context, which is what was asked for.

## Cross-context fixture: `shippingMode`

`quote`'s `ShippingMode` enum is `OCEAN_FCL` / `OCEAN_LCL` / `AIR`;
`booking`'s is ocean-only (`OCEAN_FCL` / `OCEAN_LCL` — see the "Shared
Concepts" section of
[`5_build_domain_language_output/domain_language.md`](5_build_domain_language_output/domain_language.md)).
A workflow that let the caller supply `AIR` would create a quote
successfully and then fail at the booking step. Rather than let that
surface as a runtime failure, the workflow's own `inputs.shippingMode`
schema is restricted to the two ocean values up front, so an invalid mode
is rejected before `createQuote` ever runs.

## Cross-context fixture: cargo detail

`quote`'s `CargoDetailRequest` and `booking`'s `BookingCargoDetailRequest`
both compose the shared `CargoDimensionsInput` shape, but booking also
requires a `valueUsd` (and accepts optional `marksAndNumbers`) that quote
doesn't ask for. Rather than modeling two separate cargo inputs, the
workflow declares one `cargoDetails` input carrying the superset of
fields and sends it to both steps as-is — `createQuote`'s schema has no
`additionalProperties: false`, so the extra booking-only fields are
accepted and ignored there.

## Method

1. Read `quote/quote.yaml`, `booking/booking.yaml`, and
   `logistics/logistics.yaml` in full (paths, operationIds, request/
   response schemas) rather than working from
   [`domain_language.md`](5_build_domain_language_output/domain_language.md)'s
   summaries, since an Arazzo document has to reference exact
   `operationId`s and exact response field names/JSON pointers.
2. Traced the required-field chain across the three request schemas
   (`CreateQuoteRequest`, `CreateBookingRequest`, `DispatchTruck`) to
   confirm every field each step needs is either produced by an earlier
   step's response or has to come from the workflow caller as a fresh
   input — the two "cross-context fixture" cases above surfaced from
   this pass.
3. Wrote `workflows[0].inputs` as a single JSON Schema object covering
   every value the three steps need collectively (25 properties, 14
   required — the gap is mostly optional logistics fields like
   `driverId`/`truckingVendorId`/`vehicleReference` and the per-cargo-item
   properties nested under the `cargoDetails` array items), rather than
   one inputs object per step, so a caller invokes the whole workflow
   with one payload.
4. Wired each step's `requestBody.payload` using Arazzo runtime
   expressions — `$inputs.<name>` for caller-supplied values,
   `$steps.<stepId>.outputs.<name>` for values produced by an earlier
   step — and declared `outputs` on each step (`$response.body#/<path>`)
   for exactly the fields the next step or the workflow-level `outputs`
   needs, not the full response.
5. Prefixed every `operationId` with its source description's name
   (`quoteApi.createQuote`, `bookingApi.createBooking`,
   `logisticsApi.dispatchOutbound`) — the three operationIds happen to be
   globally unique already, but the spec recommends the prefixed form to
   resolve unambiguously, so it's used throughout rather than relying on
   uniqueness holding by accident.

## Verification

- Loaded `arazzo.yaml` with a YAML parser and checked structurally: the
  `arazzo` field reads `1.1.0`; `info.title`/`info.version` are present;
  all 3 `sourceDescriptions` are `type: openapi` and named `quoteApi`/
  `bookingApi`/`logisticsApi`; the one workflow has exactly 3 steps, each
  with exactly one of `operationId`/`operationPath`/`workflowId` set and
  a `successCriteria` entry; every `inputs.required` name exists in
  `inputs.properties`.
- Confirmed each of the three `sourceDescriptions[].url`s
  (`quote/quote.yaml`, `booking/booking.yaml`, `logistics/logistics.yaml`)
  resolves to a real file in this folder, and each referenced
  `operationId` (`createQuote`, `createBooking`, `dispatchOutbound`)
  actually appears in that file — not just plausible-looking names.
- Manually walked the response-field JSON pointers used in step
  `outputs` (`#/id`, `#/quoteReference`, `#/bookingReference`, `#/stage`,
  `#/dispatches/0/id`, etc.) against the actual `QuoteResponse`,
  `BookingResponse`, and `Logistics` schema files to confirm each field
  name and nesting is correct.

## Totals

| | Count |
|---|---|
| Source OpenAPI specs referenced | 3 (`quote`, `booking`, `logistics`) |
| Workflows | 1 (`quoteToOutboundDispatch`) |
| Steps | 3 |
| Workflow-level inputs (properties / required) | 25 / 14 |
| Step-level outputs defined | 12 (`createQuote`: 7, `createBooking`: 2, `dispatchOutboundTruck`: 3) |
| Workflow-level outputs | 6 |
| New files added this step | 1 (`arazzo.yaml`) |

## Branch history

- `69015df` — *"adding workflow"* — created this branch off
  `5_build_domain_language` and committed the empty placeholder
  `steps/6_arazzo_workflow.md`.
- `3f26cb5` — merge of `origin/5_build_domain_language` into this branch,
  bringing forward that branch's `5_build_domain_language` →
  `5_build_domain_language_output` folder rename and its
  `domain_language.md` glossary commit.
- This commit — adds `steps/6_arazzo_workflow_output/` (a file-for-file
  copy of `5_build_domain_language_output`'s 150 schema files and 7 spec
  `.yaml` files, minus `domain_language.md`) plus the new `arazzo.yaml`,
  and fills in this doc.
