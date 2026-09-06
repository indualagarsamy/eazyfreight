# Eazy Freight Service

Spring Boot service implementing the **target model** from the Eazy Freight workshop
specifications. Three bounded contexts so far: **Quote**, **Booking** and
**Export Compliance**.

Tech stack and project layout follow `ddd26_claude_skills`. The domain design follows
`eazyfreight-generator/docs/target-model/` — deliberately *not* the monolith patterns
those specs describe.

---

## Stack

| | |
|---|---|
| Java | 21 (Gradle toolchain) |
| Framework | Spring Boot 3.3.2 — Web, Data JPA, Validation, Actuator |
| Database | PostgreSQL 16, schema managed by Flyway (`ddl-auto: validate`) |
| Tests | JUnit 5 + MockMvc against in-memory H2 (65 tests) |
| Tracing | Micrometer Tracing → OTLP → Jaeger |
| Boilerplate | Lombok |
| API client | Bruno collection in `docs/bruno/Eazy Freight` |

## Running it

Java 21 is required. If it was installed keg-only via Homebrew:

```shell
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
```

```shell
./gradlew build               # compiles and runs all 65 tests; no docker needed
```

```shell
docker compose up -d          # postgres on 5432, jaeger UI on 16686
./gradlew bootRun             # service on 8080
curl localhost:8080/actuator/health
```

Bruno collection:

```shell
brew install bruno
```

Open `docs/bruno/Eazy Freight`. Run the quote chain first (Create → Build → Send →
Accept), then the booking chain (Create → Submit → Confirm → Send Confirmation →
Truck Delivery Order → Dispatch). Copy the `id` from each create response into the
`quoteId` / `bookingId` collection variables.

---

## Layout

```
com.eazyfreight
├── common/         ReferenceGenerator, BusinessDays, Clock bean
├── exception/      ApiError, GlobalExceptionHandler, not-found + domain-rule exceptions
├── quote/          Quote aggregate, Rate aggregate, screening port
├── booking/        Booking aggregate
└── compliance/     EEI filing aggregate, ITN records, simulated AES boundary
```

One flat package per bounded context, holding its entities, enums, events, DTO
records, repository, service and controller — the same shape as the reference
project's `booking/` package.

Contexts do not share domain types. `quote.ShippingMode` has three values
(`OCEAN_FCL`, `OCEAN_LCL`, `AIR`); `booking.ShippingMode` has two, because this
context does not handle air. The duplication is intentional.

---

## How this differs from the monolith

The specifications describe a legacy system alongside the target model. The
differences below are the ones the code is built around.

| Monolith | Here |
|---|---|
| `Status VARCHAR(30)` of magic strings, shared across the whole shipment lifecycle | `BookingStatus` / `QuoteStatus` enums, one per context, transitions enforced by the aggregate root |
| Status changes overwrite the field; no history | `booking_status_history`, append-only, every entry naming actor, reason and source |
| Reinstatement overwrites ETD and vessel — original lost | `booking_reinstatements` captures the sailing left behind; the booking keeps its id and reference |
| One `Rate` figure; margin invisible | Buy and sell on every `QuoteLine`; `margin()` on the aggregate |
| Denied party screening done manually on a government site, if at all | `DeniedPartyScreeningClient` port; `Quote.send()` refuses unless screening cleared |
| Chargeable weight calculated in Excel | `ChargeableWeight`, a pure function with direct unit tests |
| Container payload never validated | Enforced at submission from `ContainerType` limits |
| `TDOGenerated BIT` flag | `TruckDeliveryOrder` entity with its own reference and lifecycle |
| Booking confirmation could be sent regardless of ETD change | Rule 5 gate: a >2 business day ETD variance blocks the confirmation until acknowledged |

### Aggregates

**Quote** — root `Quote`, children `QuoteLine[]`, `QuoteCargoDetail[]`. No public
setters. `send()`, `accept()`, `decline()`, `expire()` are the only ways status
moves.

**Rate** — root `Rate`, children `Surcharge[]`. Expired rates are returned by
lookups with an `expired` flag rather than filtered out, so operations can see the
lane is stale.

**Booking** — root `Booking`, children `CarrierBooking`, `BookingCargoDetail[]`,
`BookingStatusHistory[]` (append-only), `TruckDeliveryOrder`,
`BookingReinstatement[]` (append-only).

**EEIFiling** — root `EEIFiling`, children `ItnRecord[]` (append-only),
`EEIFilingHistory[]` (append-only), `ExportLicense`. Two rules shape it: a filing is
immutable once CBP has seen it, so a correction or amendment is a *new* filing
pointing at its parent; and an amendment supersedes the previous ITN rather than
replacing it, leaving both on file for the five-year retention period.

`Lane`, `Carrier` and party identifiers are referenced by id, not owned.

### Domain events

Each context declares a sealed `QuoteEvent` / `BookingEvent` interface with a record
per event. Aggregates extend Spring Data's `AbstractAggregateRoot` and register
events, which are published when the aggregate is saved — an event cannot escape
without its state change having been committed.

`QuoteAcceptedListener` in the booking context consumes `QuoteEvent.QuoteAccepted`
after commit. It logs the handoff rather than creating a booking, because a booking
needs shipper and consignee party ids that the quote does not carry.

### Tests

| | |
|---|---|
| `QuoteLifecycleTest`, `BookingLifecycleTest` | The state machines, with no Spring context — the rules live on the aggregates, so they need no database |
| `ChargeableWeightTest` | The W/M and volumetric arithmetic that used to live in a spreadsheet cell |
| `QuoteControllerTest`, `BookingControllerTest` | Full HTTP flows including the refusal paths — screening gate, payload limit, ETD acknowledgement, reinstatement identity |
| `MigrationSchemaTest` | Flyway + `ddl-auto: validate`, so the schema cannot drift from the mappings |

### API shape

Lifecycle changes are POSTs to named sub-resources (`/send`, `/accept`,
`/carrier-confirmation`, `/reinstate`), not a PUT that lets a caller assign status
directly. There is no `PUT /api/bookings/{id}`.

`X-Actor` on booking commands names who is acting and is written into the audit
trail. A real deployment would take this from the authenticated principal.

Error responses use the shared `ApiError` shape:

* `400` — bean validation failure, with per-field detail
* `404` — unknown quote or booking
* `409` — `DomainRuleViolationException`; the command was refused by the aggregate

---

## Export compliance — the CBP boundary

**No filing reaches CBP.** Submitting Electronic Export Information is a report to
the United States government, lawful only for a party holding an AES filer
certification. The `AesFilingClient` port has exactly one implementation,
`SimulatedAesFilingClient`, which fabricates responses locally. There is no endpoint
configured anywhere and no property that would switch a live adapter on, because no
live adapter exists.

Four things keep it that way:

* `AesBoundaryTest` fails the build if a second `AesFilingClient` appears, if the
  wired one stops reporting itself simulated, or if an AES endpoint property shows
  up. Adding a real client is a deliberate act with a failing test attached.
* A startup `WARN` states that filings are simulated.
* `GET /api/compliance/filing-system` reports it, and the UI shows a standing banner
  driven by that answer rather than a hard-coded string.
* Simulated ITNs use `X99999999…` where a genuine ITN carries the filing date, so a
  demo ITN can never be mistaken for one CBP issued.

The eleven commands from the specification are all implemented. Notably:

* **Correction vs amendment** are distinct. A correction replaces a filing CBP never
  accepted, so no ITN exists and the replacement is an Original. An amendment
  changes an accepted filing, so an ITN exists and is superseded on acceptance.
* **`ItnNumberReceived`** is consumed by the Booking context, which is how
  `Booking.itnFiled` gets set — the manual "mark ITN filed" endpoint is gone.
* **`BookingEvent.ItnAmendmentRequired`**, raised on reinstatement since the booking
  work, finally has a consumer.

## Known gaps

* **Not yet implemented:** the five post-booking tracks — Container & Equipment,
  Export Compliance, Documentation, Finance & Invoicing, Alerts.
* **Not modelled:** Customer, Party and Carrier are referenced by `UUID` only. There
  are no tables for them and no foreign keys.
* **Stubbed:** `StubDeniedPartyScreeningClient` clears everything except names
  containing `DENIED`. Replace before any real use.
* **Not implemented:** INTTRA submission and status pull, PDF generation, and email
  delivery. The specs define these; the events that would drive them are raised.
* **Simplified:** `BusinessDays` skips weekends only — no public holiday calendar.
* **No payload limit for 45HC** is published in the spec, so payload validation is
  skipped for that container type rather than guessed at.
* **Postgres-only constraints are not covered by the H2 test.**
  `db/migration-postgres` holds a partial unique index (one active ITN per booking)
  and a regex CHECK that H2 cannot parse, so `MigrationSchemaTest` never sees them.
  They are exercised only by running against real PostgreSQL — which is how a write
  ordering bug in ITN supersession was found, after the H2 test passed.
* **HS code is not translated to Schedule B.** `ScheduleBCode` marks the Conformist
  boundary and flags the code as untranslated rather than pretending the two are
  interchangeable. A real translation needs the Census Bureau table.
* **Shipper EIN, carrier SCAC and consignee country are keyed by hand** on the
  filing, because no Party or Carrier context exists.
* **Migrations are verified two ways.** `MigrationSchemaTest` runs Flyway against H2
  in PostgreSQL mode with `ddl-auto: validate`, so schema drift fails the build with
  no Docker needed. `V1`/`V2` have also been applied to a real PostgreSQL 16.15
  container with Hibernate validation passing, and both API flows exercised against
  it. The remaining tests follow the reference project — H2, `ddl-auto: update`,
  Flyway off.
* **Single-tenant.** The SaaS vision calls for tenant isolation; no tenant column
  exists yet.
