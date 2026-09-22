# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot service implementing the **target model** for a DDD workshop: evolving
a fictional 25-year-old freight-forwarding monolith (documented in
`docs/eazy-freight-domain-summary-v3.md`) into a multi-tenant SaaS platform. The
domain design follows `eazyfreight-generator/docs/target-model/` — deliberately
*not* the monolith patterns those specs describe. Sample/reference OpenAPI specs
per bounded context live in `docs/sample_specs/`.

Seven bounded contexts exist under `com.eazyfreight`: `quote`, `booking`,
`compliance`, `logistics`, `documentation`, `finance`, `alerts`. (Note:
`README.md` predates the last four and only describes quote/booking/compliance —
trust the source tree over the README for current scope.)

## Commands

```shell
# Java 21 required (Gradle toolchain). If installed keg-only via Homebrew:
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew build                              # compile + run all tests, no Docker needed
./gradlew test                                # tests only
./gradlew test --tests "QuoteLifecycleTest"   # single test class
./gradlew test --tests "*.QuoteLifecycleTest.shouldExpire"  # single test method

docker compose up -d                          # postgres:5432, jaeger UI:16686
./gradlew bootRun                             # service on :8080
curl localhost:8080/actuator/health

./gradlew --stop                              # kill a bootRun already bound to 8080, then bootRun again
```

Frontend (React + Vite, port 5173, proxies `/api` to `:8080` — backend must be running):

```shell
cd frontend
npm install
npm run dev          # http://localhost:5173
npm run build         # tsc -b && vite build
npm run lint          # oxlint

# Playwright end-to-end tests. Needs the service up (docker compose up -d + bootRun);
# the Vite server is started automatically. Every run records a video per test.
npm run test:e2e
npm run test:e2e -- e2e/quote-to-booking.spec.ts   # the green happy-path flow only
npm run test:e2e:report                            # open the HTML report with videos
E2E_SLOW_MO=0 npm run test:e2e                     # full speed, for CI
```

API testing: Bruno collection at `docs/bruno/Eazy Freight` (`brew install bruno`).
Run the quote chain first (Create → Build → Send → Accept), then booking
(Create → Submit → Confirm → Send Confirmation → Truck Delivery Order →
Dispatch), copying each `id` into the `quoteId`/`bookingId` collection variables.

## Architecture

**One flat package per bounded context** — `quote/`, `booking/`, `compliance/`,
etc. — each holding its own `domain/`, `dto/`, `event/`, `exception/`,
`listener/`, `repository/`, `service/`, `controller/`. Contexts do **not** share
domain types: `quote.ShippingMode` has three values (`OCEAN_FCL`, `OCEAN_LCL`,
`AIR`); `booking.ShippingMode` has two (this context doesn't handle air). The
duplication is intentional, not an oversight to fix.

`Lane`, `Carrier`, and party identifiers are referenced by `UUID` only — there
are no `Party`/`Carrier` tables or foreign keys yet.

### Aggregates

- **Quote** — root `Quote`, children `QuoteLine[]`, `QuoteCargoDetail[]`. No
  public setters; `send()`, `accept()`, `decline()`, `expire()` are the only
  ways status moves. `send()` refuses unless denied-party screening cleared.
- **Rate** — root `Rate`, children `Surcharge[]`. Expired rates are returned by
  lookups with an `expired` flag rather than filtered out.
- **Booking** — root `Booking`, children `CarrierBooking`,
  `BookingCargoDetail[]`, `BookingStatusHistory[]` (append-only),
  `TruckDeliveryOrder`, `BookingReinstatement[]` (append-only). A >2 business
  day ETD variance blocks confirmation until acknowledged (Rule 5 gate).
- **EEIFiling** (compliance) — root `EEIFiling`, children `ItnRecord[]`
  (append-only), `EEIFilingHistory[]` (append-only), `ExportLicense`. A filing
  is immutable once CBP has seen it: a correction/amendment is a *new* filing
  pointing at its parent, and an amendment supersedes the previous ITN rather
  than replacing it (five-year retention).

### Domain events

Each context declares a sealed `<Context>Event` interface with one record per
event. Aggregates extend Spring Data's `AbstractAggregateRoot` and register
events, published only after the aggregate is saved — an event cannot escape
without its state change being committed. Example: `QuoteAcceptedListener` in
`booking` consumes `QuoteEvent.QuoteAccepted` post-commit but only logs the
handoff, since booking creation needs shipper/consignee party ids the quote
doesn't carry. `ItnNumberReceived` (compliance) is consumed by booking to set
`Booking.itnFiled`.

### API shape

Lifecycle changes are POSTs to named sub-resources (`/send`, `/accept`,
`/carrier-confirmation`, `/reinstate`) — never a PUT that lets a caller assign
status directly. `X-Actor` on booking commands names who is acting and is
written to the audit trail (a real deployment would take this from the
authenticated principal instead).

Errors use the shared `ApiError` shape: `400` bean-validation failure (per-field
detail), `404` unknown quote/booking, `409` `DomainRuleViolationException`
(aggregate refused the command).

There is no springdoc/springfox dependency and no `/v3/api-docs` endpoint — any
OpenAPI spec has to be derived by reading controllers/DTOs directly (see the
`spring-openapi` and `spring-controllers` skills under `.claude/skills/`).

### Export compliance — the CBP boundary

**No filing reaches CBP.** `AesFilingClient` has exactly one implementation,
`SimulatedAesFilingClient`, fabricating responses locally; no property exists
to switch on a live adapter. Guarded four ways: `AesBoundaryTest` fails the
build if a second `AesFilingClient` appears or the wired one stops reporting
itself simulated; a startup `WARN` states filings are simulated;
`GET /api/compliance/filing-system` reports it and the UI shows a banner driven
by that answer; simulated ITNs use `X99999999…` so they can never be mistaken
for a CBP-issued one. Correction vs. amendment are distinct commands (see
Aggregates above) — don't conflate them when extending compliance.

`StubDeniedPartyScreeningClient` clears everything except names containing
`DENIED` — replace before any real use.

### Testing

- `*LifecycleTest` classes (e.g. `QuoteLifecycleTest`, `BookingLifecycleTest`)
  test the state machines with **no Spring context** — rules live on the
  aggregates, so no database is needed. Prefer this style for new aggregate
  rules.
- `*ControllerTest` classes drive full HTTP flows including refusal paths
  (screening gate, payload limit, ETD acknowledgement, reinstatement identity).
- `MigrationSchemaTest` runs Flyway against H2 in PostgreSQL mode with
  `ddl-auto: validate`, so schema drift fails the build without Docker.
  **Postgres-only constraints are not covered by this test** —
  `db/migration-postgres` holds a partial unique index and a regex `CHECK`
  H2 can't parse; a write-ordering bug in ITN supersession was only found by
  running against real Postgres. When touching migrations, consider verifying
  against a real Postgres 16 container, not just `./gradlew build`.
- H2 uses `ddl-auto: update` with Flyway off for ordinary tests (only
  `MigrationSchemaTest` turns Flyway on).
- `frontend/e2e/` holds Playwright tests that drive the real UI against a running
  service — no request interception. `quote-to-booking.spec.ts` walks enquiry →
  priced → sent → accepted → booking → submitted → confirmed → customer confirmed →
  EEI filing, plus the screening refusal. **`booking-integrity.spec.ts` fails on
  purpose**: it asserts a booking keeps the requested ETD that was typed (not the
  ETA) and cargo weight in kilograms, and `BookingService.createBooking` currently
  does neither. Those assertions are right — don't relax them to get a green run.
  Videos, traces and the HTML report land in `frontend/e2e-results/` and
  `frontend/e2e-report/` (both gitignored).

### Known gaps (don't "fix" these without asking — they're deliberate workshop scope cuts)

- Post-booking tracks beyond what's listed above may still be partial per
  context — check `docs/eazy-freight-domain-summary-v3.md` for the full target
  scope before assuming something is missing by mistake.
- `Customer`, `Party`, `Carrier` are UUID references only, no tables.
- INTTRA submission/status pull, PDF generation triggers, and email delivery:
  specs define these, events that would drive them are raised, but no consumer
  exists yet for some of them.
- `BusinessDays` skips weekends only — no public holiday calendar.
- No payload limit is published for the 45HC container type, so validation is
  skipped for it rather than guessed.
- HS code is not translated to Schedule B (`ScheduleBCode` flags this boundary
  explicitly rather than pretending they're interchangeable) — needs the
  Census Bureau table.
- Single-tenant — no tenant column exists yet despite the SaaS vision requiring
  tenant isolation.
