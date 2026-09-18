---
name: domain-language
description: Build or refresh a ubiquitous-language glossary (domain_language.md) from this project's per-context JSON Schema files. Use when asked to document domain concepts/glossary, explain what the schemas mean, or produce a "domain language" doc for the bounded contexts (alerts, booking, compliance, documentation, finance, logistics, quote).
---

# Domain language glossary

Turns a directory of `*.schema.json` files, organized one folder per bounded
context, into a single Markdown glossary a domain expert or new engineer can
read top to bottom per context to learn what each concept *means* — not a
field-by-field schema dump. See `steps/5_build_domain_language.md` and
`steps/5_build_domain_language_output/domain_language.md` for the reference
instance of this (7 contexts, 150 schema files); reread it before starting if
the target directory has a different shape.

## 1. Read every schema per module, not by hand-picking files

For each bounded-context folder (e.g. `alerts/`, `booking/schemas/`), `find`
+ `cat` every `*.schema.json` in it into one pass rather than opening files
one at a time — the point is to read a whole module's shape at once so
cross-references and duplication are visible. Also read the module's `.yaml`
spec (e.g. `alerts/alerts.yaml`) wherever a schema's purpose isn't obvious
from its fields alone.

Do this for every module in scope before writing anything — don't write up
module 1 from memory while module 2 is still unread.

## 2. Classify every schema into one of four roles

Base the classification on the schema's shape and how other schemas
reference it, not on the flat file list order:

- **Core entity/aggregate** — the root object the context revolves around
  (e.g. `BookingResponse`, `Logistics`). A module can have more than one if
  it owns several independently-lifecycled records rather than one
  aggregate with sub-objects (e.g. a House BOL, a Master BOL, and
  Instructions are three separate things) — call that out explicitly rather
  than forcing a single "the entity" framing.
- **Supporting entities / value objects** — objects composed into the core
  entity or returned alongside it (e.g. `Dispatch`, `Seal`).
- **Enumerations** — `type: string` + `enum` schemas. Write these as a table
  of value → business meaning, using the schema's `description` where
  present and inferring meaning from usage (which entity references it, what
  the values look like) where absent. Never invent, drop, or reword a value.
- **Commands** — imperative-named request schemas (`RecordSeal`,
  `CancelBookingRequest`) representing a state-changing operation, kept
  separate from response/view schemas even when they share most fields.

## 3. Follow every `$ref`

Cross-check every `$ref` while reading so the write-up reflects the actual
composition graph — document a schema as "`X` plus these extra fields," not
as a flat, independent field list, whenever it composes another schema.

## 4. Pull shared/duplicated schemas into one "Shared Concepts" section

Modules in this project duplicate a handful of schemas into their own
`common/` subfolder instead of `$ref`-ing across the context boundary (e.g.
`ContainerType`, `ShippingMode`, `Currency`, `MonetaryAmount`,
`CargoDimensionsInput`/`View`). Explain each of these once, up top, instead
of once per context — and verify with `diff` (not assumption) whether each
duplicate pair is byte-identical or a hand-synced subset, since that's a
meaningful fact about the codebase worth stating precisely.

## 5. Verify before presenting

- Every schema file's `title` must appear at least once in the glossary,
  either under its own heading or folded into a parent entity's write-up.
- Every enum's value list in the glossary must match its schema file
  exactly.
- Re-run the `diff` check on shared-schema pairs rather than trusting an
  earlier write-up of the same claim.
- Recompute the coverage numbers (schema files, distinct write-ups after
  merging shared duplicates, enums documented, commands documented) directly
  from the files rather than copying a prior count.

## Output

Write the glossary to `domain_language.md` inside the schema directory being
documented (not the repo root), and open with a coverage table — one row per
module, columns for schema file count, core entity/aggregate name(s), enums
documented, commands documented — plus a totals table (schema files,
bounded contexts, distinct write-ups, enums, commands). If producing the
companion `steps/N_*.md` process doc (method + coverage + verification +
commits), match the structure and tone of `steps/5_build_domain_language.md`.
