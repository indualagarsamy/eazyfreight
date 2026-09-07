# Arazzo Workflow

An [Arazzo](https://spec.openapis.org/arazzo/latest.html) 1.1.0 document chaining one endpoint each from the `quote`, `booking`, and `logistics` OpenAPI specs into a single new-shipment flow.

## quoteToOutboundDispatch — 3 steps
`6_arazzo_workflow_output/arazzo.yaml`

| Step | operationId | Source spec | Endpoint |
|---|---|---|---|
| createQuote | `quoteApi.createQuote` | `quote/quote.yaml` | POST /api/quotes |
| createBooking | `bookingApi.createBooking` | `booking/booking.yaml` | POST /api/bookings |
| dispatchOutboundTruck | `logisticsApi.dispatchOutbound` | `logistics/logistics.yaml` | POST /api/logistics/bookings/{bookingId}/outbound-dispatch |

## Inputs (25 top-level)

| Property | Required | Source |
|---|---|---|
| customerId | yes | `quoteApi.CreateQuoteRequest`, `bookingApi.CreateBookingRequest` |
| shipperId | yes | `bookingApi.CreateBookingRequest` (quote has no shipper id, only name/address) |
| consigneeId | yes | `bookingApi.CreateBookingRequest` (quote has no consignee id, only name/address) |
| shippingMode | yes | `quoteApi.CreateQuoteRequest`, `bookingApi.CreateBookingRequest` — restricted to `OCEAN_FCL`/`OCEAN_LCL`, dropping quote's `AIR`, since booking's enum is ocean-only |
| originPortCode | yes | `quoteApi.CreateQuoteRequest`, `bookingApi.CreateBookingRequest` |
| destinationPortCode | yes | `quoteApi.CreateQuoteRequest`, `bookingApi.CreateBookingRequest` |
| incoterms | yes | `quoteApi.CreateQuoteRequest`, `bookingApi.CreateBookingRequest` |
| currency | yes | `quoteApi.CreateQuoteRequest` only (ISO-4217 pattern from `common/Currency.schema.json`) |
| shipperName | yes | `quoteApi.CreateQuoteRequest` only |
| shipperAddress | no | `quoteApi.CreateQuoteRequest` only |
| shipperCountry | no | `quoteApi.CreateQuoteRequest` only |
| consigneeName | yes | `quoteApi.CreateQuoteRequest` only |
| consigneeAddress | no | `quoteApi.CreateQuoteRequest` only |
| consigneeCountry | no | `quoteApi.CreateQuoteRequest` only |
| requestedEtd | yes | optional on `quoteApi.CreateQuoteRequest`, required on `bookingApi.CreateBookingRequest` |
| pickupAddress | yes | optional on `bookingApi.CreateBookingRequest`, required on `logisticsApi.DispatchTruck` |
| pickupDateTime | no | `bookingApi.CreateBookingRequest` only |
| deliveryAddress | yes | `logisticsApi.DispatchTruck` only |
| scheduledPickupDate | no | `logisticsApi.DispatchTruck` only |
| scheduledDeliveryDate | no | `logisticsApi.DispatchTruck` only |
| driverId | no | `logisticsApi.DispatchTruck` only |
| truckingVendorId | no | `logisticsApi.DispatchTruck` only |
| vehicleReference | no | `logisticsApi.DispatchTruck` only |
| actor | no | `X-Actor` header on `bookingApi`/`logisticsApi`, default `operations` |
| cargoDetails | yes | `quoteApi.CreateQuoteRequest`, `bookingApi.CreateBookingRequest` (array; item shape below) |

## `cargoDetails` item properties (12)

| Property | Required | Source |
|---|---|---|
| description | yes | `common/CargoDimensionsInput.schema.json`, identical in `quoteApi`/`bookingApi` |
| hsCode | yes | `common/CargoDimensionsInput.schema.json` — pattern `\d{4}\.\d{2}\.\d{4}` |
| pieces | yes | `common/CargoDimensionsInput.schema.json` |
| weightKg | yes | `common/CargoDimensionsInput.schema.json` |
| lengthCm | yes | `common/CargoDimensionsInput.schema.json` |
| widthCm | yes | `common/CargoDimensionsInput.schema.json` |
| heightCm | yes | `common/CargoDimensionsInput.schema.json` |
| hazmat | no | `common/CargoDimensionsInput.schema.json` |
| temperatureControlled | no | `common/CargoDimensionsInput.schema.json` |
| oversized | no | `common/CargoDimensionsInput.schema.json` |
| valueUsd | yes | `bookingApi.BookingCargoDetailRequest` only — booking-only field, ignored by `createQuote` |
| marksAndNumbers | no | `bookingApi.BookingCargoDetailRequest` only — booking-only field, ignored by `createQuote` |

## Outputs

| Step | Outputs | Source (`$response.body#/...`) |
|---|---|---|
| createQuote | quoteId, quoteReference, customerId, shippingMode, originPortCode, destinationPortCode, incoterms | `QuoteResponse` |
| createBooking | bookingId, bookingReference | `BookingResponse` |
| dispatchOutboundTruck | logisticsId, logisticsStage, dispatchId | `Logistics` (`dispatchId` from `dispatches/0/id`) |

Workflow-level `outputs`: quoteId, quoteReference, bookingId, bookingReference, logisticsId, logisticsStage.

## Notes

- None of `quote`/`booking`/`logistics` `$ref` each other — each bounded context duplicates the concepts it needs (see [`5_build_domain_language.md`](5_build_domain_language.md)). This workflow is where the cross-context call sequence is expressed instead.
- `acceptQuote`/`sendQuote` were considered for the quote step instead of `createQuote`, but both need a quote to already exist and don't add a new source spec — `createQuote` keeps exactly one step per context.
- Every `operationId` is prefixed with its source description's name (`quoteApi.`/`bookingApi.`/`logisticsApi.`) per the spec's recommended disambiguation form, even though the three operationIds are already globally unique.
- Since the underlying JSON Schema files rarely carry per-property descriptions themselves, every input's `description` in `arazzo.yaml` names the exact source schema/property it maps to instead of restating the property name.
- `arazzo.yaml` is otherwise the only new file in `6_arazzo_workflow_output/`, which is a straight copy of [`5_build_domain_language_output`](5_build_domain_language_output) (150 schema files + 7 specs, minus `domain_language.md`).
- Verified: all 3 `sourceDescriptions[].url`s and all 3 `operationId`s resolve to real files/operations; every `inputs.required` name exists in `inputs.properties`; every response-field JSON pointer used in step `outputs` matches the actual `QuoteResponse`/`BookingResponse`/`Logistics` schemas; all 37 input properties (25 top-level + 12 `cargoDetails` item properties) carry a description.
- To view the workflow as a diagram rather than raw YAML: [Arazzo Playground](https://arazzo.connethics.com/) (paste and view, no install), [Arazzo Visualizer for VS Code](https://medium.com/@himethkbw/from-openapi-endpoints-to-runnable-api-workflows-introducing-arazzo-visualizer-for-vs-code-6a73b6d4b6c6), [Jentic's Arazzo UI](https://jentic.com/product/arazzo-ui), [API Flows Studio](https://github.com/API-Flows/api-flows-studio).
- Branch history: `69015df` ("adding workflow") created this branch off `5_build_domain_language` with the empty placeholder doc; `3f26cb5` merged `origin/5_build_domain_language` in, bringing forward that branch's folder rename to `5_build_domain_language_output` and its `domain_language.md` commit; everything in this doc is from the pending commit on top of those.

**Total: 3 source specs, 1 workflow, 3 steps, 37 input properties, 12 step outputs, 6 workflow outputs.**
