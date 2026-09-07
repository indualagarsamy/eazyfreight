# Entities JSON Schemas

The `components.schemas` from each of the seven OpenAPI specs in
[`2_generate_openapi_specs_output`](./2_generate_openapi_specs_output)
extracted into standalone [JSON Schema](https://json-schema.org/) (draft
2020-12) files, one per schema, with each spec rewritten to `$ref` them
instead of embedding them inline.

Written to
[`3_entities_json_schemas_output`](./3_entities_json_schemas_output), one
subfolder per module:

```
3_entities_json_schemas_output/<module>/
  <module>.yaml          # the spec, components.schemas now $ref-ing ./schemas/
  schemas/
    <SchemaName>.schema.json   # one standalone file per component schema
```

## Method

Done with a small Python script (PyYAML + a recursive converter), not by
hand — 142 schemas across 7 files is too much surface for manual
transcription to be reliable. For each spec:

1. Parse the yaml, walk `components.schemas`.
2. For every schema, recursively convert OpenAPI 3.0-isms to JSON Schema:
   - `nullable: true` next to `type: X` → `type: [X, "null"]` (JSON Schema
     has no `nullable` keyword; a type union is the standard equivalent).
   - Internal `$ref: '#/components/schemas/X'` → `$ref: 'X.schema.json'`,
     since the schema now lives in its own file, addressed by its
     sibling's filename.
   - Everything else (`enum`, `properties`, `required`, `items`,
     `additionalProperties`, `format`, `minimum`/`minLength`/`minItems`,
     `uniqueItems`, `description`) is already valid JSON Schema and passed
     through unchanged.
3. Write each converted schema to its own file, with `$schema`
   (`https://json-schema.org/draft/2020-12/schema`), `$id` (the filename),
   and `title` (the schema name) added.
4. Replace `components.schemas.<Name>` in the spec with a single-line
   `$ref: './schemas/<Name>.schema.json'` stub. Nothing else in the spec
   changes — `paths`, `parameters`, `requestBody`, and `responses` still
   reference `#/components/schemas/<Name>`, which now resolves through
   that stub to the external file, so the spec's operations are untouched.

## Verification

- Every extracted `.json` file and rewritten `.yaml` file parses.
- For every module: `paths` and `info` are byte-for-byte identical
  (semantically) to the source spec; the extracted schema names match the
  source's `components.schemas` keys exactly; every stub is exactly
  `{$ref: './schemas/<Name>.schema.json'}`; every referenced file exists.
- All 108 internal `$ref`s across the 142 extracted schema files resolve
  to an existing sibling file (no dangling refs).
- All 142 schema files pass `Draft202012Validator.check_schema` (the
  `jsonschema` Python library) — each is a structurally valid JSON Schema
  document on its own.
- End-to-end instance validation: loaded `alerts/schemas/AlertView.schema.json`
  into a `jsonschema` validator backed by a `referencing.Registry` over
  its sibling files (so `$ref`s resolve across file boundaries, the way a
  real consumer would use these), validated a realistic `AlertView`
  instance (passed), then mutated one enum field to an invalid value
  (correctly rejected) — confirming the cross-file `$ref` graph actually
  works, not just that each file is independently well-formed.

**Known gap, not introduced here:** OpenAPI 3.0 doesn't allow `nullable`
as a sibling of a bare `$ref` (only alongside `type`), so a handful of
component-typed fields that are nullable at runtime — e.g. `AlertView
.escalatedTo` (`$ref: RecipientRole`, null until an alert escalates) —
aren't marked nullable in the source spec and therefore aren't marked
nullable in the extracted schema either. This is a pre-existing accuracy
gap in the step 2 specs, carried over faithfully rather than silently
"fixed" during extraction, since fixing it would mean guessing which
`$ref` fields are actually nullable rather than just relocating what step
2 already asserted.

## Schemas by module

### alerts (18)
`AlertCategory` (enum) · `AlertHistoryAction` (enum) · `AlertStatus` (enum) ·
`AlertTrack` (enum) · `AlertType` (enum) · `AlertView` (object) ·
`ConfigurationView` (object) · `Dashboard` (object) · `DeliveryStatus` (enum) ·
`EvaluateAllResult` (object) · `EvaluateBookingResult` (object) ·
`HistoryEntry` (object) · `NotificationChannel` (enum) ·
`NotificationView` (object) · `RecipientRole` (enum) · `Resolve` (object) ·
`Snooze` (object) · `UpdateConfiguration` (object)

### booking (19)
`BookingCargoDetailRequest` (object) · `BookingCargoDetailResponse` (object) ·
`BookingReinstatementResponse` (object) · `BookingResponse` (object) ·
`BookingSourceType` (enum) · `BookingStatus` (enum) ·
`BookingStatusHistoryResponse` (object) · `CancelBookingRequest` (object) ·
`CancellationInitiator` (enum) · `CarrierBookingResponse` (object) ·
`ContainerType` (enum) · `CreateBookingRequest` (object) ·
`RecordCarrierConfirmationRequest` (object) ·
`RecordCarrierRejectionRequest` (object) · `RecordCounterOfferRequest` (object) ·
`ReinstateBookingRequest` (object) · `ShippingMode` (enum) ·
`StatusChangeSource` (enum) · `SubmitBookingRequest` (object)

### compliance (13)
`AmendFilingRequest` (object) · `CancelFilingRequest` (object) ·
`CompileEEIDataRequest` (object) · `EEIFilingHistoryResponse` (object) ·
`EEIFilingResponse` (object) · `ExportLicenseResponse` (object) ·
`FilingStatus` (enum) · `FilingSystemStatus` (object) · `FilingType` (enum) ·
`ItnRecordResponse` (object) · `RecordAcceptanceRequest` (object) ·
`RecordExportLicenseRequest` (object) · `RecordRejectionRequest` (object)

### documentation (24)
`AmendHouseBOL` (object) · `CarrierQuery` (object) ·
`CompileInstructions` (object) · `Discrepancy` (object) · `Distribute` (object) ·
`Distribution` (object) · `DistributionChannel` (enum) ·
`DistributionRecipient` (enum) · `FreightTerms` (enum) ·
`GenerateHouseBOL` (object) · `House` (object) · `HouseBOLStatus` (enum) ·
`Instructions` (object) · `InstructionsStatus` (enum) · `Master` (object) ·
`MasterBOLCorrection` (object) · `MasterBOLReceived` (object) ·
`Originals` (object) · `Preconditions` (object) · `ReleaseOriginals` (object) ·
`ReleaseType` (enum) · `SurrenderOriginals` (object) ·
`VerificationStatus` (enum) · `VoidHouseBOL` (object)

### finance (24)
`AssignResponsibility` (object) · `CalculateStorageFee` (object) ·
`CarrierInvoice` (object) · `CarrierPaymentMade` (object) ·
`CreditHoldRequest` (object) · `CreditHoldView` (object) · `CreditNote` (object) ·
`InvoiceLineView` (object) · `InvoiceStatus` (enum) · `InvoiceType` (enum) ·
`InvoiceView` (object) · `Line` (object) · `PayableStatus` (enum) ·
`PayableView` (object) · `PaymentMethod` (enum) · `PaymentTermsType` (enum) ·
`PaymentView` (object) · `PrepareInvoice` (object) · `RecordPayment` (object) ·
`StorageFeeCause` (enum) · `StorageFeeResponsibility` (enum) ·
`StorageFeeView` (object) · `UpdateLines` (object) · `VoidInvoice` (object)

### logistics (26)
`ActualCargo` (object) · `ActualCargoView` (object) · `AddressType` (enum) ·
`AwaitingDispatch` (object) · `ContainerSource` (enum) · `ContainerType` (enum) ·
`Dispatch` (object) · `DispatchStatus` (enum) · `DispatchTruck` (object) ·
`Examination` (object) · `ExaminationHold` (object) ·
`ExaminationRelease` (object) · `ExaminationResult` (enum) ·
`ItnGateStatus` (object) · `LoadedOnVessel` (object) · `Logistics` (object) ·
`LogisticsStage` (enum) · `MovementType` (enum) ·
`RecordContainerNumber` (object) · `RecordSeal` (object) · `Seal` (object) ·
`SealDeactivationReason` (enum) · `SealSource` (enum) · `Terminal` (object) ·
`TerminalGateReceipt` (object) · `TerminalGateRejection` (object)

### quote (18)
`AcceptQuoteRequest` (object) · `BuildQuotationRequest` (object) ·
`CargoDetailRequest` (object) · `CargoDetailResponse` (object) ·
`ChargeUnit` (enum) · `CreateQuoteRequest` (object) ·
`DeclineQuoteRequest` (object) · `QuoteLineRequest` (object) ·
`QuoteLineResponse` (object) · `QuoteLineType` (enum) ·
`QuoteResponse` (object) · `QuoteStatus` (enum) · `RateResponse` (object) ·
`RateType` (enum) · `ScreeningStatus` (enum) · `ShippingMode` (enum) ·
`SurchargeResponse` (object) · `SurchargeType` (enum)

## Totals

| Module | Schemas extracted | Output |
|---|---|---|
| alerts | 18 | `3_entities_json_schemas_output/alerts/` |
| booking | 19 | `3_entities_json_schemas_output/booking/` |
| compliance | 13 | `3_entities_json_schemas_output/compliance/` |
| documentation | 24 | `3_entities_json_schemas_output/documentation/` |
| finance | 24 | `3_entities_json_schemas_output/finance/` |
| logistics | 26 | `3_entities_json_schemas_output/logistics/` |
| quote | 18 | `3_entities_json_schemas_output/quote/` |

**Total: 142 schemas across 7 modules** — matching the schema counts from
[`2_generate_openapi_specs.md`](./2_generate_openapi_specs.md) exactly.
