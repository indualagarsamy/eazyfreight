---
name: automate-testing
description: Turn an Arazzo workflow document into Playwright tests that execute it as data (not hand-coded requests) against the real running eazyfreight backend, fixing whatever the live run surfaces — a workflow missing a precondition step, backend bugs, or test-fixture gaps. Use when asked to automate/write end-to-end tests for an Arazzo workflow, turn a workflow doc into a passing integration test, or verify a workflow actually runs against the live backend.
---

# Automate testing

Builds a small Arazzo executor plus Playwright tests that read a workflow
document (e.g. `steps/6_arazzo_workflow_output/arazzo.yaml`) and drive its
steps as real HTTP calls against the live backend — so the test *is* the
document: add/remove/reorder a step in the YAML and the test follows without
a code change. See `steps/7_automate_testing.md` and
`steps/7_automate_testing_output/_integration_tests/` for the reference
instance (5 steps, quote → booking → logistics); reread it before starting
if the target workflow has a different shape or step count.

This is not "write some Playwright tests" — the point is that running the
generated suite against a **real, live backend** is what finds bugs no
amount of re-reading the OpenAPI specs or the Arazzo document would catch.
Budget for backend bugs and workflow gaps turning up; finding and fixing
them (with the user's sign-off) is part of this skill, not a detour from it.

## 1. Write the Arazzo executor

One TypeScript module (`src/arazzoRunner.ts` in the reference instance) that:

- Parses the target `arazzo.yaml` plus every spec in its `sourceDescriptions`,
  and builds an `operationId -> {method, path}` index from each spec's
  `paths`.
- Resolves each step's prefixed `operationId`
  (`<sourceDescription.name>.<operationId>`) to that index.
- Threads runtime expressions between steps: `$inputs.<name>` from the
  workflow's caller-supplied inputs, `$steps.<id>.outputs.<name>` from an
  earlier step's already-captured outputs.
- After each HTTP call, checks `successCriteria` (`$statusCode == <n>`
  conditions) and captures `outputs` (`$response.body#/<json-pointer>`,
  including through array indices).
- Explicitly scope it, in a header comment, to the subset of Arazzo the
  target document actually uses (see the reference file's header) — this is
  a test harness for one document, not a general-purpose Arazzo runtime.
  Grow it only if the target document starts using a feature outside that
  scope; don't build out unused generality up front.

## 2. Write the tests

- **Runner unit tests** (no network): operation-index resolution against the
  real workflow file + specs, runtime-expression resolution, and a full run
  of every step against a stateful in-memory fake backend that mimics the
  real API's status-code/response shape for each operation.
- **Integration test(s)** against the live backend: run the whole workflow
  end to end and assert the final step's success status; add one negative
  test per precondition discovered in step 4 below, asserting the exact
  error status/body the backend returns when that precondition isn't met
  (don't just assert "it fails" — pin the real behavior).
- Scaffold `package.json` (`@playwright/test` + `js-yaml`),
  `playwright.config.ts` (`baseURL` from an env var, default
  `http://localhost:8080`), `tsconfig.json`. Make `test:unit` / `test:e2e` /
  `test` (both) separate npm scripts so the unit tests can run without a
  live backend. Have the e2e run skip with a message (not fail) if the
  backend isn't reachable at `baseURL` — don't assume Docker/Gradle are
  available in every environment that runs `npm test`.

## 3. Run it against the real, running backend

Per `RUNNING.md`: `docker compose up -d` then `./gradlew bootRun`, poll
`/actuator/health` until it's up. Then `npm install && npm test` in the
tests folder. A clean pass on the first try is the exception, not the
expectation — work through failures in this order:

## 4. When a step 409s / fails for a reason invisible in the OpenAPI spec

If a later step's precondition (e.g. a status the target resource must
already be in) isn't encoded anywhere in the request/response schemas —
only in the service's own code — the workflow document itself is
incomplete, not just the test. Read the actual service method enforcing the
precondition (grep for the resource's status check, e.g.
`requireConfirmedBooking`-style guards) to find exactly what state is
required and which earlier operation(s) in the same spec produce it.

**Flag this to the user before acting** — extending the workflow with the
missing step(s), asserting the failure as expected behavior instead, or
dropping the step that can't be satisfied are all legitimate calls, but
they change what the document claims the business process is, so it's the
user's call, not a default. If extending: add the new step(s) to
`arazzo.yaml` in the right position, thread any new required fields into
`inputs` (reconcile against every step's request schema the way the
`arazzo-workflow` skill does — same rules for shared fields, narrowed
enums, per-property descriptions), and keep the negative test proving the
skipped-precondition case still 409s as documented in step 2.

## 5. When the live backend itself is broken

A request that's valid per the spec but 404s/500s/misbehaves against the
running server is a real backend bug, not a test bug. Before touching any
`src/main/java` file:

- Confirm it's not a test-data problem first (wrong id, stale fixture,
  wrong order) — reproduce with a direct `curl` outside the test.
- Find the root cause in the actual source (don't patch symptoms): compare
  what the framework/library is documented to do against what's actually
  configured (e.g. check `/actuator/mappings` if routing is suspect), and
  isolate the mechanism with a minimal direct check before concluding it's
  a framework-version quirk rather than a config mistake.
- **Flag the specific fix to the user before making it** — this skill's own
  job is the tests, not unsupervised backend changes; getting sign-off on
  digging into and fixing a `src/main/java` bug is a precondition, not an
  afterthought.
- Fix precisely the reported bug's mechanism, not everything that looks
  similar — e.g. if an enum-mapping helper breaks for one enum because of a
  naming quirk specific to it, fix that call site/enum, don't rewrite the
  helper into a blanket reflection-based fix covering enums that never hit
  the quirk.
- After each source change: restart the backend
  (`./gradlew --stop && ./gradlew bootRun` — `--stop` kills every Gradle
  daemon, which is also however `bootRun` needs to be stopped), poll
  `/actuator/health`, then re-run the test *and* re-check with direct
  `curl` that the fix didn't change behavior for adjacent cases (other
  routes, other enum values, other parameter kinds) that were already
  working.

## 6. When a 409 is a genuine business rule the test fixture just didn't supply

Not every failure against a live backend is a bug — some are correct
business logic the request schema marks optional but the service actually
requires under certain conditions (e.g. "one of A or B", "C required when
B is chosen"). Distinguish this from section 4/5 by checking: is the field
already in the schema (just not supplied by the test), and is the
requirement documented as intentional server-side validation rather than a
missing schema/routing/mapping defect? If so, it's a fixture gap — add the
field to the test's input fixture, not to the Arazzo document or the
backend.

## 7. Verify before presenting

Don't declare success from one green run:

- `tsc --noEmit` clean.
- The whole suite (unit + integration) passes **twice in a row** against
  the live backend, for stability.
- If backend source changed: run the backend's own test suite too (e.g.
  `./gradlew test`) and account for every failure — for any failure that
  predates this change, prove it with `git stash` (stash the backend
  changes, re-run that one failing test, confirm it fails identically) so
  it can be called pre-existing rather than assumed so.
- For every backend bug fixed, confirm live with direct `curl` (not just
  the Playwright re-run) both that the bug is gone and that adjacent,
  previously-working behavior is unaffected.
- Use direct DB inspection (e.g. `docker exec ... psql`) when HTTP-level
  evidence alone doesn't explain a result — for example when two tests in
  the same run each create their own instance of a resource and you need
  to confirm which one a given error actually belongs to.

## Output

New `_integration_tests/` folder (or equivalent) alongside the workflow's
output folder: the runner module, the two spec files, Playwright/TS
scaffolding, a `.gitignore` for `test-results/`/`playwright-report/`, and a
`README.md` explaining setup/running/why-the-step-count-is-what-it-is. If
producing the companion `steps/N_*.md` process doc, match the structure of
`steps/7_automate_testing.md`: steps table, output-files table, a "bugs
found and fixed" section per bug (symptom, root cause, fix, what wasn't
touched and why), a test-fixture-gaps section, a Verification section, a
Notes section (judgment calls flagged/confirmed, restart procedure), a
totals line, and a Branch history section.
