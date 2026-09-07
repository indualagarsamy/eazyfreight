# Automate Testing

Playwright tests that execute [`6_arazzo_workflow.md`](6_arazzo_workflow.md)'s Arazzo workflow — read as data, not hand-coded — against the real eazyfreight backend.

## quoteToOutboundDispatch — 5 steps (was 3)
`7_automate_testing_output/arazzo.yaml`

| Step | operationId | Method | Status |
|---|---|---|---|
| createQuote | `quoteApi.createQuote` | POST /api/quotes | 201 |
| createBooking | `bookingApi.createBooking` | POST /api/bookings | 201 |
| submitBooking | `bookingApi.submitBooking` | POST /api/bookings/{id}/submit | 200 |
| recordCarrierConfirmation | `bookingApi.recordCarrierConfirmation` | POST /api/bookings/{id}/carrier-confirmation | 200 |
| dispatchOutboundTruck | `logisticsApi.dispatchOutbound` | POST /api/logistics/bookings/{bookingId}/outbound-dispatch | 201 |

`submitBooking` and `recordCarrierConfirmation` are new in this step. Writing the tests surfaced that `LogisticsService.dispatchOutbound` (`requireConfirmedBooking`, `src/main/java/com/eazyfreight/logistics/service/LogisticsService.java:351`) refuses to run unless the booking is `CONFIRMED_BY_CARRIER` or `CUSTOMER_CONFIRMED` — a precondition invisible in the OpenAPI schemas, only visible in the service code. The original 3-step chain (`createQuote -> createBooking -> dispatchOutbound`) would have failed step 3 with a 409. `arazzo.yaml`'s `inputs` grew from 25 to 33 properties (20 required) to carry the new `carrierId`/`carrierBookingRef`/`vesselName`/`voyageNumber`/`confirmedEtd`/`confirmedEta`/`containerType`/`numberOfContainers` fields those two steps need.

## `7_automate_testing_output/_integration_tests/`

| File | Purpose |
|---|---|
| `src/arazzoRunner.ts` | Parses `arazzo.yaml` + its 3 source specs, resolves each step's `operationId` to a real HTTP call, threads `$steps.<id>.outputs.*` between steps, checks `successCriteria`. Scoped to the subset of Arazzo this document uses (see the file's header comment) — not a general-purpose runtime. |
| `tests/arazzo-runner.unit.spec.ts` | Runner unit tests: operation-index resolution against the real `arazzo.yaml`, runtime-expression resolution, and a full 5-step run against a stateful in-memory fake backend. No network. |
| `tests/quote-to-dispatch.spec.ts` | Integration test against a live backend, plus a second test asserting the exact 409 `dispatchOutbound` returns against an unconfirmed booking. |
| `package.json`, `playwright.config.ts`, `tsconfig.json` | `@playwright/test` + `js-yaml`, `baseURL` from `EAZYFREIGHT_BASE_URL` (default `http://localhost:8080`). |

Run with `npm install && npm test` inside that folder (`npm run test:unit` / `test:e2e` to run one file). Details in [`_integration_tests/README.md`](7_automate_testing_output/_integration_tests/README.md).

## Backend bugs found and fixed while getting the e2e test to pass

Running the test against a real, running backend (`docker compose up -d` +
`./gradlew bootRun`, per `RUNNING.md`) surfaced three pre-existing backend
bugs that no amount of reading the OpenAPI specs would have caught — none
of them are in anything this project's earlier steps produced; all three
are in hand-written `src/main/java` code this step didn't create.

### 1. Trailing-slash routing: 4 of 7 controllers' root endpoint 404'd

`GET`/`POST /api/quotes`, `/api/bookings`, `/api/logistics`, `/api/alerts`
all 404'd, while `/api/compliance`, `/api/documentation`, `/api/finance`
worked. Cause: those 4 modules' OpenAPI spec defines a collection-root
operation at path `"/"`, which Spring combines with the controller's
class-level `@RequestMapping("/api/quotes")` into the pattern
`/api/quotes/` — **with** a trailing slash. Spring Framework 6's default
`PathPatternParser`-based matching requires an exact trailing-slash match
(Spring 5 / Boot 2's `AntPathMatcher` didn't enforce this as strictly),
so a plain `GET /api/quotes` — the form every spec, the Arazzo workflow,
and any real client uses — 404'd even though the route was registered
correctly.

Confirmed via `/actuator/mappings` (temporarily exposed) that the pattern
really was `/api/quotes/`, and via direct testing that neither
`PathMatchConfigurer.setUseTrailingSlashMatch(true)` nor an explicit
`PathPatternParser` with `matchOptionalTrailingSeparator(true)` fixes
this — both are documented Spring 6 mechanisms, but the direction they
support (a pattern *without* a slash matching a request *with* one) is
the reverse of what's needed here (pattern *has* the slash, request
doesn't) — proven by writing out `AntPathMatcher.match("/api/quotes/",
"/api/quotes")` directly and observing it returns `false`. **Fix:**
`@Override @RequestMapping(method = ..., value = {"", "/"}, ...)` added
directly to the 6 affected controller methods (`getAllQuotes`/`createQuote`
in `QuoteController`, `getAllBookings`/`createBooking` in
`BookingController`, `getAll` in `LogisticsController`, `open` in
`AlertController`), registering both path forms explicitly rather than
relying on any trailing-slash leniency setting.

### 2. `BookingApiMapper.mapEnum` used `.name()` instead of `.toString()`

`createBooking` 500'd with `No enum constant
com.eazyfreight.booking.domain.ShippingMode.FCL`. The generated
`com.eazyfreight.booking.model.ShippingMode` (from `booking.jar`) has
Java constants literally named `FCL`/`LCL` — openapi-generator stripped
the `OCEAN_` prefix shared by *both* of booking's ShippingMode values
(quote's 3-value version, which includes `AIR`, has no shared prefix and
keeps `OCEAN_FCL`/`OCEAN_LCL` as-is). `mapEnum`'s generic
`Enum.valueOf(targetType, source.name())` looked up the Java constant
name; the generated enum's `toString()` correctly returns the wire value
(`"OCEAN_FCL"`) regardless of the constant's name. **Fix:** changed the
one generic helper to use `source.toString()`.

### 3. `BookingApiMapper.toModel`'s reverse ShippingMode mapping

The mirror image of #2: mapping the *domain* enum (`OCEAN_FCL`) back to
the *generated model* enum failed the same way, since `Enum.valueOf`
still can't find a constant named `OCEAN_FCL` when it's actually named
`FCL`. `toString()` doesn't help here (the source is a plain domain enum
without special-cased naming). **Fix:** this one call site now uses the
generated enum's own `ShippingMode.fromValue(String)` factory instead of
`mapEnum`, since that's the type's own wire-value parser and handles the
rename correctly.

None of these were touched preemptively elsewhere — `ContainerType`,
`BookingSourceType`, `CancellationInitiator`, and `StatusChangeSource`
(booking's other enums) have no shared-prefix values, so they don't hit
the same `openapi-generator` naming quirk; #2/#3 are scoped to
`ShippingMode` specifically, not applied as a blanket reflection-based
fix to `mapEnum`.

## Test-fixture gaps found the same way

Two more `409`s turned out to be genuine business rules the test's own
`freshInputs()` fixture simply hadn't supplied, not backend bugs:
`LogisticsService.dispatchOutbound` requires either a `driverId` or a
`truckingVendorId` on the dispatch, and a trucking-vendor dispatch
additionally requires a `vehicleReference` "for audit." Both are optional
per `DispatchTruck`'s schema, so nothing upstream (the spec, the Arazzo
doc, TypeScript) flagged their absence — only the live backend's business
logic did. Fixed by adding `truckingVendorId` and `vehicleReference` to
`freshInputs()`.

## Verification

- `npx tsc --noEmit` — clean.
- `npm test` (backend running per `RUNNING.md`) — **10/10 passed**: all 8
  unit tests plus both integration tests, run twice in a row for
  stability.
- Confirmed live, before and after each fix, with direct `curl` calls
  (not just re-running the test) that: all 7 controllers' root endpoints
  return `200`/`201` instead of `404`; the trailing-slash form
  (`/api/quotes/`) still also works; query/path/body parameter binding
  on the 6 patched methods is unaffected (`GET /api/alerts?category=...`
  still filters; `POST` with an invalid body still `400`s, not `404`).
- `./gradlew test` (the backend's own test suite): 168/169 passed. The
  one failure (`QuoteControllerTest.aMalformedHsCodeIsRejectedWithFieldLevelDetail`,
  an unrelated Bean Validation message-text mismatch) was confirmed
  **pre-existing** by `git stash`-ing all 5 backend source changes and
  re-running that single test against the original code — it fails
  identically either way, so it's not a regression from this step.
- `docker exec eazyfreight-postgres psql` used throughout to inspect
  `bookings`/`booking_status_history` directly when HTTP-level evidence
  alone didn't explain a result (e.g. confirming *which* booking a 409
  actually belonged to, since two tests in the same file each create
  their own booking).

## Notes

- `node_modules/` is already covered by the repo's root `.gitignore`; added a local `_integration_tests/.gitignore` for `test-results/`/`playwright-report/`.
- The extra two workflow steps (`submitBooking`/`recordCarrierConfirmation`) were a judgment call flagged to and confirmed with the user before writing (extend the workflow to a real passing happy path, vs. asserting the failure, vs. dropping the third step).
- The backend fix (dig into the 4-controller routing gap and correct it) was likewise flagged and confirmed with the user before any `src/main/java` file was touched.
- Backend restarted via `./gradlew --stop && ./gradlew bootRun` after every source change in this section, each time polling `/actuator/health` before re-testing — `./gradlew --stop` kills every Gradle daemon, including whichever one is running `bootRun`, so it doubles as the "stop the server" step.

**Total: 1 Arazzo workflow (5 steps, up from 3), 1 runner module, 2 spec files, 10 tests (8 unit + 2 integration, all passing live), 3 backend bugs fixed across 5 `src/main/java` files, 2 test-fixture gaps fixed.**

## Branch history

- `69015df` — *"adding workflow"* — created the `6_arazzo_workflow` branch
  off `5_build_domain_language` with the empty placeholder step doc.
- `3f26cb5` — merge of `origin/5_build_domain_language`, bringing forward
  that branch's folder rename to `5_build_domain_language_output` and its
  `domain_language.md` glossary.
- `24217d6` — *"Add Arazzo workflow chaining quote, booking, and
  logistics"* — the original 3-step `arazzo.yaml` and
  `6_arazzo_workflow.md` (step 6).
- `ec47ecf` — *"Added arazzo spec"* — carried `7_automate_testing_output/`
  forward from step 6's output and extended `arazzo.yaml` from 3 steps to
  the 5-step happy path described above, plus per-property input
  descriptions.
- `f2252d7` — *"Added Playwright tests"* — the `_integration_tests/`
  folder in full: `arazzoRunner.ts`, both spec files, and the Playwright
  project scaffolding.
- `03e1149` — *"Fixed APIs"* — the three backend fixes: the
  trailing-slash `@RequestMapping` overrides on `QuoteController`,
  `BookingController`, `LogisticsController`, and `AlertController`, and
  the two `BookingApiMapper` enum-mapping fixes.
- This doc — written after `03e1149`, once all 10 tests were confirmed
  passing against a live backend; pending commit.
