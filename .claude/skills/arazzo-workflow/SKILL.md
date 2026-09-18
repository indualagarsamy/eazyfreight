---
name: arazzo-workflow
description: Build an Arazzo 1.1.0 workflow document chaining one operation each from several of this project's per-context OpenAPI specs into a single business-process flow (e.g. quote -> booking -> dispatch). Use when asked to build/create an Arazzo workflow, chain endpoints across bounded contexts into one flow, or express a cross-context business process as an executable API sequence.
---

# Arazzo workflow

Turns N independent OpenAPI specs (one per bounded context, none `$ref`-ing
each other — see the `domain-language` skill/doc) into a single Arazzo 1.1.0
document that sequences one operation from each into the real-world business
process they jointly implement. See `steps/6_arazzo_workflow.md` and
`steps/6_arazzo_workflow_output/arazzo.yaml` for the reference instance
(quote -> booking -> logistics, 3 steps, 37 input properties); reread it
before starting if the target flow has a different shape.

## 1. Pick the steps

- Pick exactly one operation per source spec that advances the same
  real-world object through its lifecycle (a quote becomes a booking
  becomes a dispatch) — not just any operation that happens to share
  fields.
- When more than one operation on a spec could plausibly serve as "the"
  step, write down the alternatives considered and why they lost (in the
  reference instance, `acceptQuote`/`sendQuote` were considered instead of
  `createQuote` and rejected because both require a quote to already
  exist). This becomes a Notes bullet in the companion doc.
- One step per source spec keeps the workflow's shape legible; don't add a
  second step against the same spec unless the process genuinely needs two
  separate calls into that context.

## 2. Read every operation's request/response schema before writing inputs

For each step's operation, read its full request body schema and success
response schema (follow every `$ref`, including into `common/` duplicated
schemas). Do this for every step before drafting `inputs` — don't write
step 1's inputs from memory while step 2's schema is still unread.

## 3. Build `sourceDescriptions`

One entry per source spec: `name` (a short `camelCase` label used to prefix
operationIds, e.g. `quoteApi`), `url` (relative path to the spec file from
the Arazzo document), `type: openapi`.

## 4. Reconcile the request schemas into one `inputs` object

This is the hard part — the same real-world fact (customer id, shipping
mode, port code) is often required by more than one step's request schema,
sometimes under the same name and sometimes not, and not always with the
same optionality or the same enum. For every field across every step's
request schema:

- **Same field needed by multiple steps** → one workflow input, fed to
  every step's payload that needs it via `$inputs.<name>` for the first
  step that produces it, and via `$steps.<earlier>.outputs.<name>` for
  every later step — *if* an earlier step's output already carries that
  value back (reuse what came back over resupplying the raw input; see
  `createBooking`'s payload in the reference instance, which pulls
  `customerId`/`shippingMode`/port codes from `$steps.createQuote.outputs`
  rather than `$inputs`, even though those are also top-level inputs).
- **Field only one step needs** → still a workflow input (Arazzo has no
  per-step input scoping), with a description noting which step uses it
  and that the others ignore it.
- **Required in one step, optional/absent in another** → the workflow
  input is required if *any* step that needs it requires it, even if an
  earlier step in the chain treats it as optional (e.g. `requestedEtd` is
  optional on the quote but required on the booking → required at the
  workflow level).
- **Narrower enum downstream** → restrict the workflow input's `enum` to
  the intersection, not the union, so an upstream step can never produce a
  value a downstream step rejects (e.g. `shippingMode` drops the quote's
  `AIR` because the booking's enum is ocean-only). Say so in both the
  input's `description` and the workflow `description`.
- **Cross-cutting header** (e.g. an actor/audit header repeated on several
  operations) → one input with the same default the operations document,
  fed to every step via a `parameters` entry (`in: header`), not folded
  into any payload.
- **Array/object item schemas duplicated across specs** (e.g. a cargo line
  item) → one shared item schema in `inputs`, verified field-by-field
  identical (or noted where it isn't) across every spec that uses it, plus
  any fields only one step's item schema has (mark those in the
  `description` as ignored by the other steps).
- Every property in `inputs` — top-level and nested item properties —
  gets a `description` naming the *exact* source schema and property it
  maps to (`<sourceDescription>.<SchemaName>.<property>`), since the
  underlying JSON Schemas rarely carry per-property descriptions
  themselves. This is not optional polish — it's how a reader later
  verifies the mapping without re-deriving it.

## 5. Write the `steps`

- `operationId` is `<sourceDescription.name>.<operationId>` — prefix every
  one this way even when the bare operationId is already globally unique,
  per the spec's recommended disambiguation form.
- `requestBody.payload` maps each field to `$inputs.<name>` or
  `$steps.<earlier>.outputs.<name>` per the rules in step 4 above.
- Path/header parameters that come from an earlier step's result (e.g. a
  booking id in a later step's URL) use `$steps.<earlier>.outputs.<name>`
  in `parameters`, not `$inputs`.
- `successCriteria` checks the expected success status code.
- `outputs` pulls every response field a later step or the workflow-level
  `outputs` will need, as `$response.body#/<pointer>` — including into
  nested arrays where relevant (e.g. `dispatches/0/id`).

## 6. Set workflow-level `outputs`

A subset of the step outputs — the ids/references a caller of the whole
workflow actually wants back (not every intermediate pass-through field).

## 7. Verify before presenting

Run this pass yourself, unasked, against the actual files — don't just
re-read the YAML and assert it's fine:

- Every `sourceDescriptions[].url` resolves to a real file, and every
  step's `operationId` (minus its prefix) resolves to a real operation in
  that file.
- Every name in `inputs.required` — top-level and inside every nested item
  schema — exists in the matching `properties`.
- Every `$response.body#/...` pointer in every step's `outputs` matches a
  real property (following the pointer through arrays/nesting) on that
  operation's actual success-response schema.
- Every property under `inputs` (top-level and nested) has a
  `description`.
- Report the counts you verified (source specs, steps, input properties,
  step outputs, workflow outputs) rather than asserting correctness in the
  abstract.

## Output

Copy the source step's output folder straight across into
`steps/<N>_arazzo_workflow_output/` (all specs + schemas, unchanged) and
add only `arazzo.yaml` alongside them — don't touch the copied specs or
schemas. If producing the companion `steps/N_*.md` process doc (steps
table, inputs table, nested-item-properties table, outputs table, Notes
with alternatives-considered/verification/branch-history bullets, totals
line), match the structure and tone of `steps/6_arazzo_workflow.md`.
