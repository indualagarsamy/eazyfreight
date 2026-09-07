# Domain Language

A glossary of every concept represented by the JSON Schema files in this
directory, organized by the 7 bounded contexts (`alerts`, `booking`,
`compliance`, `documentation`, `finance`, `logistics`, `quote`). Each
context's `schemas/` folder is self-contained: none of them `$ref` a
schema in a different context's folder, so each is read here as its own
vocabulary.

## Table of Contents

- [Overview](#overview)
- [Shared Concepts (duplicated per context)](#shared-concepts-duplicated-per-context)
- [Quote](#quote)
  - [Core entity](#quote-core-entity)
  - [Cargo & rating value objects](#quote-cargo--rating-value-objects)
  - [Enumerations](#quote-enumerations)
  - [Commands](#quote-commands)
- [Booking](#booking)
  - [Core entity](#booking-core-entity)
  - [Cargo & carrier value objects](#booking-cargo--carrier-value-objects)
  - [Enumerations](#booking-enumerations)
  - [Commands](#booking-commands)
- [Logistics](#logistics)
  - [Core entity](#logistics-core-entity)
  - [Sub-entities](#logistics-sub-entities)
  - [Enumerations](#logistics-enumerations)
  - [Commands](#logistics-commands)
- [Compliance](#compliance)
  - [Core entity](#compliance-core-entity)
  - [Supporting entities](#compliance-supporting-entities)
  - [Enumerations](#compliance-enumerations)
  - [Commands](#compliance-commands)
- [Documentation](#documentation)
  - [Core entities](#documentation-core-entities)
  - [Supporting entities](#documentation-supporting-entities)
  - [Enumerations](#documentation-enumerations)
  - [Commands](#documentation-commands)
- [Finance](#finance)
  - [Core entities](#finance-core-entities)
  - [Line items & payments](#finance-line-items--payments)
  - [Enumerations](#finance-enumerations)
  - [Commands](#finance-commands)
- [Alerts](#alerts)
  - [Core entity](#alerts-core-entity)
  - [Supporting entities](#alerts-supporting-entities)
  - [Enumerations](#alerts-enumerations)
  - [Commands](#alerts-commands)

## Overview

The system models an ocean-freight forwarder's operational lifecycle as
seven bounded contexts, each owning one stage of a shipment's life:

| Context | Owns |
|---|---|
| **quote** | Pricing a prospective shipment before a booking exists |
| **booking** | The customer's confirmed shipment order and the carrier's confirmation of it |
| **logistics** | Physical container movement — dispatch, sealing, customs examination, vessel loading |
| **compliance** | U.S. export filing (EEI/AES) and licensing for the shipment |
| **documentation** | Bills of lading (house/master) and shipping instructions |
| **finance** | Customer invoicing, carrier payables, storage fees, credit holds |
| **alerts** | Cross-cutting monitoring that watches all of the above and raises time-sensitive notifications |

A shipment's journey runs roughly `quote` → `booking` → (`logistics` +
`compliance` + `documentation` in parallel) → `finance`, with `alerts`
observing every track. Most contexts follow the same three-part shape:

- **A root entity/aggregate** (e.g. `BookingResponse`, `Logistics.schema.json`) representing the thing the context manages end-to-end.
- **Value objects and enums** describing its fields, statuses, and classifications.
- **Command schemas** (imperative names like `RecordSeal`, `CancelBookingRequest`) — the request bodies for state-changing operations, distinct from the response/view schemas that represent read models.

## Shared Concepts (duplicated per context)

Two concepts recur across context boundaries. Because no context `$ref`s
another's schemas, each keeps its own copy (see
[`4_consolidate_entities.md`](../4_consolidate_entities.md) for the
consolidation history):

- **ContainerType** (`booking/`, `logistics/`) — the ISO container size/type a shipment moves in: `TWENTY_GP`, `FORTY_GP`, `FORTY_HC`, `FORTY_FIVE_HC`. Byte-identical in both contexts.
- **ShippingMode** (`booking/`, `quote/`) — the transport mode: `quote/` supports `OCEAN_FCL`, `OCEAN_LCL`, `AIR`; `booking/` supports only the two ocean values, since bookings in this system are ocean-only.
- **Currency** (`finance/schemas/common/`, `quote/schemas/common/`) — an ISO-4217 3-letter currency code (`[A-Z]{3}` pattern).
- **MonetaryAmount** (`finance/schemas/common/`, `quote/schemas/common/`) — a non-negative number denominated in the sibling `currency` field; schemas needing a strictly-positive amount (e.g. `CreditNote`, `RecordPayment`) layer a stricter `minimum: 0.01` on top.
- **CargoDimensionsInput** / **CargoDimensionsView** (`booking/schemas/common/`, `quote/schemas/common/`) — the physical shape of a cargo item (description, HS code, pieces, weight, dimensions, hazmat/temperature-control/oversized flags), factored out because both `booking` and `quote` model cargo detail independently but identically at the physical level. `Input` carries request-side validation constraints (minimums, HS-code pattern); `View` is the same fields unconstrained for output.

---

## Quote

The pre-booking pricing context: given a customer's cargo and lane, produces a
priced quotation the customer can accept or decline.

### Quote: core entity

- **QuoteResponse** — the quotation itself: customer, lane (origin/destination port, incoterms), shipping mode, screening status, validity window, the carrier the customer selected, computed totals (`totalBuyRate`, `totalSellRate`, `margin`), and its lines/cargo. Produced by `CreateQuoteRequest` and `BuildQuotationRequest`.

### Quote: cargo & rating value objects

- **CargoDetailRequest** — a bare alias for the shared `CargoDimensionsInput` (adds nothing quote-specific on the request side).
- **CargoDetailResponse** — `CargoDimensionsView` plus quote-specific computed shipping metrics: `volumetricWeightCbm`/`Kg`, `chargeableWeight`, and the `chargeableUnit` it's billed in.
- **QuoteLineRequest** / **QuoteLineResponse** — one priced line item on a quote (base freight or a surcharge): a `lineType`, buy/sell rate, quantity, and charge unit. The response adds `id`, resolved `currency`, and the computed `amount`.
- **RateResponse** — a carrier's published rate for a lane: buy rate, currency, `rateType` (contract vs. spot), unit, transit days, and validity window. Expired rates are still returned (not hidden) so operations can see a lane is stale rather than silently pricing from it.
- **SurchargeResponse** — an accessorial charge (BAF, THC, etc.) attached to a rate, with its own amount, currency, and validity window.

### Quote: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **ShippingMode** | `OCEAN_FCL`, `OCEAN_LCL`, `AIR` | Transport mode for the quote |
| **QuoteStatus** | `DRAFT`, `SENT`, `ACCEPTED`, `DECLINED`, `EXPIRED` | Quote lifecycle |
| **QuoteLineType** | `BASE_FREIGHT`, `BAF`, `CAF`, `PSS`, `THC_ORIGIN`, `THC_DESTINATION`, `DOC_FEE`, `INSURANCE` | What a quote line prices — the base freight line plus every surcharge category |
| **SurchargeType** | `BAF`, `CAF`, `PSS`, `THC_ORIGIN`, `THC_DESTINATION`, `DOC_FEE` | What a *rate's* surcharge prices — a subset of `QuoteLineType` (excludes the non-surcharge `BASE_FREIGHT`/`INSURANCE` lines); kept as a separate vocabulary because it classifies a different thing (a rate surcharge vs. a quote line) |
| **RateType** | `CONTRACT`, `SPOT` | Whether a carrier rate is a standing contract rate or a one-off spot rate |
| **ChargeUnit** | `TEU`, `CBM`, `KG`, `FLAT` | The unit a rate or quote line is charged per |
| **ScreeningStatus** | `PENDING`, `CLEARED`, `FLAGGED` | Denied-party/sanctions screening outcome on the quote |

### Quote: commands

- **CreateQuoteRequest** — start a new quote: customer, lane, cargo, currency, shipper/consignee identity.
- **BuildQuotationRequest** — attach priced lines (with validity and spot-rate flag) to a quote.
- **AcceptQuoteRequest** — customer accepts, selecting a carrier.
- **DeclineQuoteRequest** — customer declines, with an optional reason.

---

## Booking

The confirmed shipment order once a customer commits, tracking the request
through carrier confirmation, cargo detail, and cancellation/reinstatement.

### Booking: core entity

- **BookingResponse** — the booking aggregate: parties (customer, shipper, consignee, notify parties), lane and shipping mode, requested vs. carrier-confirmed schedule, transport/pickup arrangements, cancellation detail, ETD-variance acknowledgement gate, plus its cargo details, carrier booking, status history, and reinstatement history.

### Booking: cargo & carrier value objects

- **BookingCargoDetailRequest** / **BookingCargoDetailResponse** — the shared `CargoDimensionsInput`/`View` plus booking-specific fields: declared `valueUsd` and `marksAndNumbers`.
- **CarrierBookingResponse** — the carrier side of the booking: booking source (direct carrier vs. co-loader), carrier reference, vessel/voyage, confirmed ETD/ETA, container type and count, and confirmation timestamps.
- **BookingStatusHistoryResponse** — an audit trail entry recording a status transition (`fromStatus` → `toStatus`), who/what changed it, and why.
- **BookingReinstatementResponse** — a record of a booking being reinstated after vessel overbooking: previous vs. new vessel/voyage/ETD/ETA, who reinstated it and why.

### Booking: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **BookingStatus** | `BOOKING_REQUESTED`, `SUBMITTED_TO_CARRIER`, `REJECTED_BY_CARRIER`, `COUNTER_OFFER_RECEIVED`, `CONFIRMED_BY_CARRIER`, `CUSTOMER_CONFIRMED`, `VESSEL_OVERBOOKED`, `CANCELLED` | Booking lifecycle |
| **ShippingMode** | `OCEAN_FCL`, `OCEAN_LCL` | Ocean-only subset of quote's mode enum |
| **ContainerType** | `TWENTY_GP`, `FORTY_GP`, `FORTY_HC`, `FORTY_FIVE_HC` | Container size/type |
| **BookingSourceType** | `DIRECT_CARRIER`, `CO_LOADER` | Whether the booking is placed directly with a carrier or through a co-loader |
| **CancellationInitiator** | `CUSTOMER`, `CARRIER_OVERBOOKING`, `OPERATIONS` | Who/what caused a cancellation |
| **StatusChangeSource** | `MANUAL`, `INTTRA`, `SYSTEM` | What triggered a status-history entry — a human, the INTTRA EDI network, or an automated system event |

### Booking: commands

- **CreateBookingRequest** — place a new booking from a quote (or standalone), with parties, lane, cargo.
- **SubmitBookingRequest** — submit the booking to a chosen carrier/co-loader.
- **RecordCarrierConfirmationRequest** — record the carrier's confirmed vessel, voyage, ETD/ETA, and container type.
- **RecordCarrierRejectionRequest** — record that the carrier rejected the booking, with a reason.
- **RecordCounterOfferRequest** — record a carrier counter-offer (proposed vessel/voyage/ETD/ETA).
- **ReinstateBookingRequest** — reinstate a booking after overbooking with new vessel/voyage/schedule.
- **CancelBookingRequest** — cancel the booking, recording who initiated it and why.

---

## Logistics

Physical execution of the shipment: moving the container from customer
pickup through terminal delivery, sealing, CBP examination, and vessel
loading.

### Logistics: core entity

- **Logistics** — the physical-movement aggregate for a booking: assigned container (number, type, source), current `LogisticsStage`, ITN-gate status (whether the inbound truck may proceed — blocked reason lives here), documentation-preconditions flag, loading/vessel-departure timestamps, active seal, and its dispatches, examinations, seal history, terminal acceptance, and actual-vs-booked cargo.

### Logistics: sub-entities

- **Dispatch** — one truck movement (outbound to customer, or inbound to the port terminal): movement type, driver/vendor/vehicle, pickup/delivery address and type, scheduled vs. actual timestamps, status, and delivery-receipt reference.
- **Seal** — a container seal: seal number, its source (customer- vs. customs-issued), active flag, issued/deactivated timestamps, deactivation reason, and a pointer to the seal that replaced it (from an examination).
- **Examination** — a CBP hold-and-inspection event: when the hold was placed/completed, the result, the seal before and after (if replaced), the CBP officer, and whether it's still open.
- **Terminal** — the port terminal's acceptance of the container: gate receipt number, terminal name, acceptance time, earliest-acceptance/cutoff dates, and — if the container arrived before the terminal would normally accept it — whether a storage fee applies and its estimated amount.
- **ActualCargo** / **ActualCargoView** — the cargo as actually weighed/counted at pickup, vs. what was booked; the view adds the booked figures, the variance, and whether it diverges materially enough to flag.
- **AwaitingDispatch** — a read-model row: a confirmed booking that needs an outbound truck but has none dispatched yet.

### Logistics: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **LogisticsStage** | `NOT_STARTED`, `OUTBOUND_DISPATCHED`, `AT_CUSTOMER`, `LOADING_COMPLETE`, `SEALED`, `INBOUND_DISPATCHED`, `AT_TERMINAL`, `UNDER_CBP_EXAMINATION`, `LOADED_ON_VESSEL`, `DEPARTED` | The container's position in the full outbound→loaded pipeline |
| **MovementType** | `OUTBOUND`, `INBOUND` | Whether a dispatch is truck-to-customer or truck-to-port |
| **AddressType** | `CARRIER_YARD`, `CUSTOMER_PREMISES`, `PORT_TERMINAL` | Classification of a dispatch's pickup/delivery address |
| **DispatchStatus** | `DISPATCHED`, `PICKED_UP`, `DELIVERED`, `FAILED` | Status of a single truck dispatch |
| **ContainerType** | `TWENTY_GP`, `FORTY_GP`, `FORTY_HC`, `FORTY_FIVE_HC` | Container size/type (shared with `booking`) |
| **ContainerSource** | `CARRIER_YARD`, `MANUAL_ENTRY` | How the container number was obtained |
| **SealSource** | `CUSTOMER_ISSUED`, `CUSTOMS_ISSUED` | Who issued a seal |
| **SealDeactivationReason** | `CUSTOMS_INSPECTION`, `DAMAGED_SEAL` | Why a seal was deactivated |
| **ExaminationResult** | `RELEASED`, `ADDITIONAL_HOLD`, `SEIZED` | Outcome of a CBP examination |

### Logistics: commands

- **RecordContainerNumber** — assign a container number, type, and source.
- **DispatchTruck** — dispatch a truck (driver, vendor, addresses, schedule).
- **RecordSeal** — record a new seal number on the container.
- **ExaminationHold** — open a CBP examination hold.
- **ExaminationRelease** — close an examination with a result and optional replacement seal.
- **TerminalGateReceipt** — record the terminal's acceptance (gate receipt, cutoff dates, storage-fee rate).
- **TerminalGateRejection** — record the terminal refusing the container, with a reason.
- **LoadedOnVessel** — record the vessel the container was loaded on.
- **ActualCargo** (also a command) — record actual weight/pieces/CBM at pickup.

---

## Compliance

U.S. export filing (Electronic Export Information / AES) for a shipment,
plus any export license it requires.

### Compliance: core entity

- **EEIFilingResponse** — an export filing: booking reference, filing reference/type, parent-filing link (for amendments), status, the full shipper/consignee/commodity data submitted to CBP, Schedule B classification (with a flag for whether it's been translated from a raw HS code or carried across untranslated), submission/acceptance/rejection/cancellation timestamps and reasons, the active ITN, whether it came from the local simulator vs. real CBP, and its export license, ITN records, and history.

### Compliance: supporting entities

- **ExportLicenseResponse** — a licensing record needed for controlled commodities: license number, issuing authority, type, ECCN, validity window, authorized value.
- **ItnRecordResponse** — one Internal Transaction Number issued for a filing (a filing can have more than one over its life, e.g. after amendment): number, issued/recorded timestamps, active flag, simulated flag, and a pointer to the ITN that superseded it.
- **EEIFilingHistoryResponse** — an audit trail entry for a filing's status transitions.

### Compliance: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **FilingStatus** | `DRAFT`, `SUBMITTED`, `ACCEPTED`, `REJECTED`, `CANCELLED` | Filing lifecycle |
| **FilingType** | `ORIGINAL`, `AMENDMENT`, `CANCELLATION` | What kind of filing this record is |
| **FilingSystemStatus** (fixed-shape response, not an enum) | — | Whether the filing backend is running against the real CBP system or a local simulator |

### Compliance: commands

- **CompileEEIDataRequest** — assemble a new EEI filing's data (shipper/consignee, commodity, quantities, value, carrier SCAC, vessel/voyage, port, destination country, ETD, license-required flag).
- **AmendFilingRequest** — amend an existing filing, with a reason.
- **CancelFilingRequest** — cancel a filing, with a reason.
- **RecordAcceptanceRequest** — record CBP's acceptance and the resulting ITN.
- **RecordRejectionRequest** — record CBP's rejection with a code and description.
- **RecordExportLicenseRequest** — attach an export license to the filing.
- **CarrierQuery** — record a carrier's data query against the filing (free-text query string).

---

## Documentation

Bills of lading and shipping instructions — the paper trail that lets the
consignee take delivery of the cargo.

### Documentation: core entities

- **Instructions** — shipping instructions sent to the carrier: booking link, reference, status, supersession chain, container/seal/ITN references, full shipper/consignee/notify-party detail, cargo description, freight terms, drafted/approved/sent timestamps, the documentation cutoff date, whether it was sent after cutoff, and any carrier query raised against it.
- **Master** — the Master Bill of Lading received back from the carrier: booking/instructions link, BOL number, when it was issued by the carrier and received, verification status, discrepancy tracking (fields, raised/resolved timestamps), and the stored document file reference.
- **House** — the House Bill of Lading issued to the shipper: booking/master link, BOL number, revision number, active flag, status, release type (and when/by whom it was confirmed), full snapshotted party/vessel/cargo data (frozen at issuance), freight terms, issue/void timestamps, supersession chain, amendment-blocked reason (an outstanding negotiable original blocks amendment), and its originals and distributions.
- **Distribution** / **Distribute** — a record of the House BOL being sent to a recipient: who (`DistributionRecipient`), how (`DistributionChannel`), revision number, reference, and when/by whom it was sent. `Distribute` is the command form; `Distribution` is the persisted record (adds `id`, `revisionNumber`, `sentAt`/`sentBy`).

### Documentation: supporting entities

- **Originals** — tracking of negotiable original House BOLs: how many issued vs. surrendered, how many outstanding, whether all are surrendered, and release/surrender detail (to whom, courier reference, timestamps).
- **Preconditions** — a read-model of what the Documentation track is still waiting on for a booking: container number, seal number, ITN number, carrier booking reference, and which of those are still missing.
- **Discrepancy** — a set of field names flagged as mismatched on a received Master BOL.

### Documentation: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **InstructionsStatus** | `DRAFT`, `APPROVED`, `SENT`, `QUERIED`, `SUPERSEDED` | Shipping-instructions lifecycle |
| **HouseBOLStatus** | `ISSUED`, `VOIDED` | House BOL lifecycle |
| **VerificationStatus** | `PENDING`, `VERIFIED`, `DISCREPANCY_RAISED`, `CORRECTED` | Master BOL verification state |
| **ReleaseType** | `ORIGINAL_BOL`, `TELEX_RELEASE`, `SEA_WAYBILL` | How the consignee obtains the cargo at destination |
| **FreightTerms** | `PREPAID`, `COLLECT` | Who pays freight charges |
| **DistributionChannel** | `EMAIL`, `PORTAL`, `COURIER` | How a document was sent |
| **DistributionRecipient** | `SHIPPER`, `CONSIGNEE_AGENT`, `NOTIFY_PARTY` | Who a document was sent to |

### Documentation: commands

- **CompileInstructions** — assemble shipping instructions from booking/logistics/compliance data, with optional field overrides.
- **GenerateHouseBOL** — issue a House BOL with a chosen release type and freight terms.
- **AmendHouseBOL** — amend an issued House BOL's party/vessel/cargo detail, with a reason.
- **VoidHouseBOL** — void a House BOL, with a reason.
- **MasterBOLReceived** — record receipt of the carrier's Master BOL.
- **MasterBOLCorrection** — record a corrected Master BOL number/document reference.
- **ReleaseOriginals** — release negotiable originals to a party, with a courier reference.
- **SurrenderOriginals** — record a count of originals surrendered back.
- **CarrierQuery** — raise a query to the carrier about an instructions discrepancy.

---

## Finance

Customer invoicing, carrier payables, storage fees, and credit control for
a booking.

### Finance: core entities

- **InvoiceView** — a customer invoice: booking/House-BOL link, status, type, payment terms, invoice/due dates, confirmed ETA, computed totals (`totalAmount`, `totalBuyAmount`, `margin`, `paidAmount`, `outstandingAmount`), currency, the reason issuance is blocked (if any — the House BOL requirement lives here), overdue flag/days, void detail, a link to the invoice it credits (for credit notes), and its lines and payments.
- **PayableView** — an amount owed to a carrier: booking/invoice/customer-payment/carrier links, amount and currency, the customer payment date that funds it (the due date is fixed at two business days after and cannot move), status, the carrier's own invoice reference/amount and the variance against it, approval and payment detail, and overdue flag.
- **StorageFeeView** — a storage fee charged for early port delivery or late BOL instructions: cause, who's responsible for it, daily rate, days, computed amount/currency, period, and the invoice it's billed on (or why invoicing is blocked).
- **CreditHoldView** — a hold placed on a customer's ability to book further shipments: which booking triggered it, reason, active flag, placed/lifted timestamps and actors.

### Finance: line items & payments

- **Line** / **InvoiceLineView** — an editable invoice line (description, buy/sell amount, quantity, unit) vs. its persisted view, which adds `id`, `lineNumber`, and the computed `extendedBuy`/`extendedSell`/`margin`. Deliberately kept as two schemas rather than merged — the same request/response DTO pattern used throughout this project (see `QuoteLineRequest`/`Response`), not accidental duplication.
- **CarrierInvoice** — a carrier's own invoice reference and amount, recorded against a payable to detect variance.
- **PaymentView** — a customer payment received against an invoice: amount, currency, date, method, reference, and who recorded it.
- **CreditNote** — a credit issued against an invoice: amount (must be strictly positive) and reason.

### Finance: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **InvoiceStatus** | `PREPARED`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `VOIDED` | Invoice lifecycle |
| **InvoiceType** | `FREIGHT`, `STORAGE_FEE`, `CREDIT_NOTE` | What the invoice bills for |
| **PayableStatus** | `PENDING`, `AWAITING_INVOICE_MATCH`, `APPROVED`, `PAID` | Carrier-payable lifecycle |
| **PaymentTermsType** | `TWO_WEEKS_BEFORE_ARRIVAL`, `NET_30` | How an invoice's due date is computed |
| **PaymentMethod** | `WIRE`, `CHECK`, `ACH` | How a payment was made |
| **StorageFeeCause** | `EARLY_PORT_DELIVERY`, `BOL_INSTRUCTIONS_LATE` | Why a storage fee accrued |
| **StorageFeeResponsibility** | `CUSTOMER`, `EAZY_FREIGHT`, `CARRIER_DISPUTED`, `UNDETERMINED` | Who ultimately carries a storage fee |

### Finance: commands

- **PrepareInvoice** — start preparing an invoice, choosing payment terms and currency.
- **UpdateLines** — replace an invoice's line items.
- **VoidInvoice** — void an issued invoice, with a reason.
- **RecordPayment** — record a customer payment (amount, date, method, reference).
- **CalculateStorageFee** — compute a storage fee (cause, daily rate, days, period, currency).
- **AssignResponsibility** — assign who's responsible for a storage fee, with notes.
- **CarrierPaymentMade** — record that a carrier payable was paid (date, reference).
- **CreditHoldRequest** — place a credit hold on a customer, with a reason.

---

## Alerts

A cross-cutting monitoring context that evaluates conditions across every
other track and raises, tracks, and delivers time-sensitive alerts about
them.

### Alerts: core entity

- **AlertView** — one raised alert: the booking it's about, its type/code, which track it belongs to, category (severity), status, title/message/recommended action, the roles it's addressed to, creation/deadline/last-evaluated timestamps, overdue flag, acknowledgement/snooze/escalation/resolution detail (each with who and when), the maximum hours it may be snoozed, its history, and the notifications sent for it.

### Alerts: supporting entities

- **HistoryEntry** — one entry in an alert's audit trail (sequence number, action, timestamp, actor, notes).
- **NotificationView** — one notification sent for an alert: channel, recipient role, sent/delivered timestamps, delivery status, failure reason, and whether it was simulated rather than actually dispatched.
- **ConfigurationView** / **UpdateConfiguration** — the tunable configuration for one alert type: enabled flag, threshold days, escalation hours, notification channels, snooze limits, custom message override, and recipients. `UpdateConfiguration` is the write form.
- **Dashboard** — the aggregate "what needs attention" view: open-alert count broken down by category and by track, unacknowledged/escalated/overdue counts, and how many bookings are affected.
- **EvaluateAllResult** / **EvaluateBookingResult** — fixed-shape results returned by the evaluation endpoints (`POST /evaluate`, `POST /bookings/{id}/evaluate`), reporting how many alerts changed (and, for the all-bookings case, how many are now open).

### Alerts: enumerations

| Enum | Values | Meaning |
|---|---|---|
| **AlertTrack** | `LOGISTICS`, `COMPLIANCE`, `FINANCE`, `BOOKING`, `DOCUMENTATION` | Which operational context the alert condition belongs to |
| **AlertCategory** | `CRITICAL`, `HIGH`, `MEDIUM`, `LOW` | How much business damage the condition does if nobody acts |
| **AlertStatus** | `ACTIVE`, `ACKNOWLEDGED`, `SNOOZED`, `ESCALATED`, `RESOLVED` | Alert lifecycle |
| **AlertHistoryAction** | `CREATED`, `NOTIFICATION_SENT`, `ACKNOWLEDGED`, `SNOOZED`, `REACTIVATED`, `ESCALATED`, `DEADLINE_RECALCULATED`, `RESOLVED` | What kind of event an alert history entry records |
| **AlertType** | 31 codes (`L001`–`L008` logistics, `C001`–`C007` compliance, `F001`–`F008` finance, `B001`–`B004` booking, `D001`–`D004` documentation) | The full catalog of monitored conditions, e.g. `L007_SEAL_NUMBER_NOT_RECORDED`, `F003_PAYMENT_OVERDUE`, `C005_ITN_MISSING_CUTOFF_BREACHED` |
| **RecipientRole** | `OPERATIONS_STAFF`, `COMPLIANCE_STAFF`, `ACCOUNTING_STAFF`, `OPERATIONS_MANAGEMENT`, `FINANCE_MANAGEMENT`, `MANAGEMENT` | Who an alert or notification is addressed to |
| **NotificationChannel** | `IN_APP`, `EMAIL`, `SMS` | How a notification is delivered |
| **DeliveryStatus** | `PENDING`, `DELIVERED`, `FAILED` | Delivery outcome of a notification |

### Alerts: commands

- **Resolve** — resolve an alert, with a reason.
- **Snooze** — snooze an alert until a given time.
- **UpdateConfiguration** — change an alert type's configuration (escalation hours, channels, snooze limits are required; threshold/enabled/message are optional).
