# API Endpoints

Identified from the Spring Boot REST controllers under `src/main/java/com/eazyfreight/**/controller/*.java`.

## quote — QuoteController (`/api/quotes`)
`src/main/java/com/eazyfreight/quote/controller/QuoteController.java`

| Method | Path |
|---|---|
| GET | /api/quotes |
| GET | /api/quotes/{id} |
| GET | /api/quotes/by-reference/{quoteReference} |
| GET | /api/quotes/open |
| GET | /api/quotes/expiring |
| GET | /api/quotes/by-customer/{customerId} |
| GET | /api/quotes/rates |
| POST | /api/quotes |
| POST | /api/quotes/{id}/rescreen |
| POST | /api/quotes/{id}/build |
| POST | /api/quotes/{id}/send |
| POST | /api/quotes/{id}/accept |
| POST | /api/quotes/{id}/decline |
| POST | /api/quotes/{id}/expire |

## booking — BookingController (`/api/bookings`)
`src/main/java/com/eazyfreight/booking/controller/BookingController.java`

| Method | Path |
|---|---|
| GET | /api/bookings |
| GET | /api/bookings/{id} |
| GET | /api/bookings/by-reference/{bookingReference} |
| GET | /api/bookings/{id}/status-history |
| GET | /api/bookings/by-customer/{customerId} |
| GET | /api/bookings/by-carrier/{carrierId} |
| GET | /api/bookings/pending-carrier-confirmation |
| GET | /api/bookings/approaching-etd |
| POST | /api/bookings |
| POST | /api/bookings/{id}/submit |
| POST | /api/bookings/{id}/carrier-confirmation |
| POST | /api/bookings/{id}/carrier-rejection |
| POST | /api/bookings/{id}/counter-offer |
| POST | /api/bookings/{id}/counter-offer/accept |
| POST | /api/bookings/{id}/counter-offer/reject |
| POST | /api/bookings/{id}/acknowledge-etd-variance |
| POST | /api/bookings/{id}/send-confirmation |
| POST | /api/bookings/{id}/vessel-overbooking |
| POST | /api/bookings/{id}/reinstate |
| POST | /api/bookings/{id}/cancel |

## compliance — ComplianceController (`/api/compliance`)
`src/main/java/com/eazyfreight/compliance/controller/ComplianceController.java`

| Method | Path |
|---|---|
| GET | /api/compliance/filing-system |
| GET | /api/compliance/filings |
| GET | /api/compliance/filings/awaiting-cbp |
| GET | /api/compliance/filings/{id} |
| GET | /api/compliance/filings/by-reference/{filingReference} |
| GET | /api/compliance/bookings/{bookingId}/filings |
| POST | /api/compliance/bookings/{bookingId}/filings |
| POST | /api/compliance/filings/{id}/compile |
| POST | /api/compliance/filings/{id}/submit |
| POST | /api/compliance/filings/{id}/acceptance |
| POST | /api/compliance/filings/{id}/rejection |
| POST | /api/compliance/filings/{id}/correct |
| POST | /api/compliance/filings/{id}/amend |
| POST | /api/compliance/filings/{id}/cancel |
| POST | /api/compliance/filings/{id}/export-license |

## documentation — DocumentationController (`/api/documentation`)
`src/main/java/com/eazyfreight/documentation/controller/DocumentationController.java`

| Method | Path |
|---|---|
| GET | /api/documentation/bookings/{bookingId}/preconditions |
| GET | /api/documentation/bookings/{bookingId}/instructions |
| GET | /api/documentation/bookings/{bookingId}/master-bols |
| GET | /api/documentation/bookings/{bookingId}/house-bols |
| GET | /api/documentation/house-bols |
| GET | /api/documentation/house-bols/{id} |
| GET | /api/documentation/house-bols/by-number/{houseBolNumber}/revisions |
| GET | /api/documentation/house-bols/{id}/pdf (PDF download) |
| POST | /api/documentation/bookings/{bookingId}/instructions |
| POST | /api/documentation/instructions/{id}/approve |
| POST | /api/documentation/instructions/{id}/send |
| POST | /api/documentation/instructions/{id}/carrier-query |
| POST | /api/documentation/instructions/{id}/resend-corrected |
| POST | /api/documentation/instructions/{id}/master-bol |
| POST | /api/documentation/master-bols/{id}/verify |
| POST | /api/documentation/master-bols/{id}/discrepancy |
| POST | /api/documentation/master-bols/{id}/correction |
| POST | /api/documentation/master-bols/{id}/house-bol |
| POST | /api/documentation/house-bols/{id}/pdf (PDF generation, returns download) |
| POST | /api/documentation/house-bols/{id}/distribute |
| POST | /api/documentation/house-bols/{id}/originals/release |
| POST | /api/documentation/house-bols/{id}/originals/surrender |
| POST | /api/documentation/house-bols/{id}/amend |
| POST | /api/documentation/house-bols/{id}/void |

## finance — FinanceController (`/api/finance`)
`src/main/java/com/eazyfreight/finance/controller/FinanceController.java`

| Method | Path |
|---|---|
| GET | /api/finance/invoices |
| GET | /api/finance/invoices/overdue |
| GET | /api/finance/invoices/{id} |
| GET | /api/finance/bookings/{bookingId}/invoices |
| GET | /api/finance/payables |
| GET | /api/finance/bookings/{bookingId}/payables |
| GET | /api/finance/storage-fees |
| GET | /api/finance/credit-holds |
| GET | /api/finance/invoices/{id}/pdf (PDF download) |
| POST | /api/finance/bookings/{bookingId}/invoice |
| POST | /api/finance/invoices/{id}/lines |
| POST | /api/finance/invoices/{id}/actuals |
| POST | /api/finance/invoices/{id}/issue |
| POST | /api/finance/invoices/{id}/send |
| POST | /api/finance/invoices/{id}/payments |
| POST | /api/finance/invoices/{id}/void |
| POST | /api/finance/invoices/{id}/credit-note |
| POST | /api/finance/payables/{id}/carrier-invoice |
| POST | /api/finance/payables/{id}/approve |
| POST | /api/finance/payables/{id}/paid |
| POST | /api/finance/bookings/{bookingId}/storage-fee |
| POST | /api/finance/storage-fees/{id}/responsibility |
| POST | /api/finance/storage-fees/{id}/invoice |
| POST | /api/finance/bookings/{bookingId}/credit-hold |
| POST | /api/finance/credit-holds/{customerId}/lift |

## logistics — LogisticsController (`/api/logistics`)
`src/main/java/com/eazyfreight/logistics/controller/LogisticsController.java`

| Method | Path |
|---|---|
| GET | /api/logistics |
| GET | /api/logistics/blocked-on-itn |
| GET | /api/logistics/awaiting-outbound-dispatch |
| GET | /api/logistics/under-examination |
| GET | /api/logistics/bookings/{bookingId} |
| GET | /api/logistics/bookings/{bookingId}/itn-gate |
| POST | /api/logistics/bookings/{bookingId}/outbound-dispatch |
| POST | /api/logistics/bookings/{bookingId}/container-number |
| POST | /api/logistics/bookings/{bookingId}/delivered-to-customer |
| POST | /api/logistics/bookings/{bookingId}/loading-complete |
| POST | /api/logistics/bookings/{bookingId}/seal |
| POST | /api/logistics/bookings/{bookingId}/seal/replace |
| POST | /api/logistics/bookings/{bookingId}/inbound-dispatch |
| POST | /api/logistics/bookings/{bookingId}/loaded-container-picked-up |
| POST | /api/logistics/bookings/{bookingId}/delivered-to-port |
| POST | /api/logistics/bookings/{bookingId}/terminal-receipt |
| POST | /api/logistics/bookings/{bookingId}/terminal-rejection |
| POST | /api/logistics/bookings/{bookingId}/examination-hold |
| POST | /api/logistics/bookings/{bookingId}/examination-release |
| POST | /api/logistics/bookings/{bookingId}/loaded-on-vessel |
| POST | /api/logistics/bookings/{bookingId}/vessel-departed |
| POST | /api/logistics/bookings/{bookingId}/actual-cargo |

## alerts — AlertController (`/api/alerts`)
`src/main/java/com/eazyfreight/alerts/controller/AlertController.java`

| Method | Path |
|---|---|
| GET | /api/alerts |
| GET | /api/alerts/dashboard |
| GET | /api/alerts/overdue |
| GET | /api/alerts/{id} |
| GET | /api/alerts/bookings/{bookingId} |
| GET | /api/alerts/configurations |
| POST | /api/alerts/evaluate |
| POST | /api/alerts/bookings/{bookingId}/evaluate |
| POST | /api/alerts/{id}/acknowledge |
| POST | /api/alerts/{id}/snooze |
| POST | /api/alerts/{id}/resolve |
| PUT | /api/alerts/configurations/{alertType} |

## Notes

- `GlobalExceptionHandler` (`@ControllerAdvice`) was excluded — it is a cross-cutting error handler, not a resource controller.
- Lifecycle/state transitions are almost universally POSTs to named sub-resources (e.g. `/send`, `/accept`, `/{id}/submit`) rather than generic PUT updates. The sole exception is `AlertController`'s `PUT /api/alerts/configurations/{alertType}`, which replaces a configuration record.
- `X-Actor` is a common `@RequestHeader` (with a default value) carrying who performed the action, for audit trail purposes — not a domain field.
- A few endpoints return `ResponseEntity<Resource>` with `produces = MediaType.APPLICATION_PDF_VALUE` for PDF downloads instead of JSON (noted above).

**Total: 7 controllers, 132 endpoints.**
