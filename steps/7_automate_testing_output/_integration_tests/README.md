# Arazzo-driven Playwright tests

Executes [`../arazzo.yaml`](../arazzo.yaml)'s `quoteToOutboundDispatch` workflow as data — `src/arazzoRunner.ts` parses the Arazzo document and its 3 source OpenAPI specs, resolves each step's `operationId` to a real HTTP call, threads `$steps.<id>.outputs.*` between them, and checks each step's `successCriteria` — rather than hand-coding the 5 requests as a fixed script. If a step is added, removed, or reordered in `arazzo.yaml`, the tests follow without a code change to the test files.

## Setup

```shell
npm install
```

## Running

```shell
npm run test:unit   # tests/arazzo-runner.unit.spec.ts — no server needed, ~1s
npm run test:e2e    # tests/quote-to-dispatch.spec.ts  — needs the real backend, see below
npm test            # both
```

`test:e2e` needs the actual eazyfreight backend running (see [`../../../RUNNING.md`](../../../RUNNING.md)):

```shell
docker compose up -d   # from the repo root: Postgres
./gradlew bootRun       # from the repo root: backend on :8080
```

If the backend isn't reachable at `EAZYFREIGHT_BASE_URL` (default `http://localhost:8080`), `test:e2e` skips with a message instead of failing — it doesn't assume Docker/Gradle are available.

## Files

- `src/arazzoRunner.ts` — the Arazzo executor. Deliberately scoped to the subset of the spec `arazzo.yaml` uses (see its top comment) rather than a general-purpose Arazzo runtime.
- `tests/arazzo-runner.unit.spec.ts` — unit tests for the runner itself: operation-index resolution against the real `arazzo.yaml` + specs, runtime-expression resolution, and a full 5-step run against a stateful fake backend (no network).
- `tests/quote-to-dispatch.spec.ts` — the real integration test against a live backend, a second test that documents the business rule the workflow's `submitBooking`/`recordCarrierConfirmation` steps exist to satisfy (`dispatchOutbound` returns 409 against an unconfirmed booking), and a third test proving the same refusal through the real frontend (`LogisticsDetailPage`) — clicking "Dispatch outbound truck" and asserting the toast, not the status code. The UI test self-skips if `EAZYFREIGHT_FRONTEND_URL` (default `http://localhost:5173`) isn't reachable, same as the backend-reachability skip for the other two.

## Known issue this surfaced

`BookingService.createBookingRequest` passes `request.requestedEta()` into both the
`requestedEtd` and `requestedEta` parameters of `Booking.request(...)` — `requestedEtd`
is never read from the request at all. Any caller that omits `requestedEta` (as the
`dispatchOutboundTruck is refused...` test above does) gets a 500 (NPE on
`requestedEtd.isBefore(...)`) instead of the 201 it expects. The UI test works around
this by sending `requestedEta` explicitly rather than fixing the service, since fixing
it was out of scope for adding UI coverage — see `BookingService.java` around the
`Booking.request(...)` call.

## Why 5 steps, not 3

The first draft of `arazzo.yaml` (see [`../../6_arazzo_workflow.md`](../../6_arazzo_workflow.md)) chained `createQuote -> createBooking -> dispatchOutbound`. Writing these tests surfaced that `LogisticsService.dispatchOutbound` (`requireConfirmedBooking`) refuses to run against a booking that isn't yet `CONFIRMED_BY_CARRIER` or `CUSTOMER_CONFIRMED` — a precondition invisible from the OpenAPI schemas alone, only visible in the actual service code. `arazzo.yaml` was extended with `submitBooking` and `recordCarrierConfirmation` between `createBooking` and `dispatchOutboundTruck` so the documented workflow is one that actually succeeds end to end.
