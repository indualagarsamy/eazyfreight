---
name: openapi-extract-json-schemas
description: Extract `components.schemas` from one or more OpenAPI 3.0 specs (produced by the spring-openapi / openapi-generate-server-jar skills) into standalone JSON Schema (draft 2020-12) files, one per schema, rewriting the spec to `$ref` them. Use when asked to extract/split out JSON schemas from an OpenAPI spec, generate standalone entity schemas, or produce a schemas/ folder alongside a spec.
---

# Extract JSON Schemas from an OpenAPI spec

Turns a spec's inline `components.schemas` into standalone JSON Schema
(draft 2020-12) files under a sibling `schemas/` folder, with the spec
rewritten to `$ref` them instead of embedding them inline. Input is
normally one or more `<module>.yaml` specs from
`steps/2_generate_openapi_specs_output/`; do it for every module the user
names, or every module in that directory if the ask is unscoped ("all
specs", "the whole API").

## Output layout

Write output to `steps/<N>_entities_json_schemas_output/<module>/` (pick
the next unused step number if this is a fresh run in this repo; reuse the
existing step's output path if asked to regenerate):

```
<module>/
  <module>.yaml               # the spec, components.schemas now $ref-ing ./schemas/
  schemas/
    <SchemaName>.schema.json  # one standalone file per component schema
```

## Method

Do this with a small Python script (PyYAML + a recursive converter), not by
hand — dozens of schemas across several files is too much surface for
manual transcription to be reliable. For each spec:

1. Parse the YAML, walk `components.schemas`.
2. For every schema, recursively convert OpenAPI 3.0-isms to JSON Schema:
   - `nullable: true` next to `type: X` → `type: [X, "null"]` (JSON Schema
     has no `nullable` keyword; a type union is the standard equivalent).
   - Internal `$ref: '#/components/schemas/X'` → `$ref: 'X.schema.json'`,
     since the schema now lives in its own file, addressed by its
     sibling's filename.
   - Everything else (`enum`, `properties`, `required`, `items`,
     `additionalProperties`, `format`, `minimum`/`minLength`/`minItems`,
     `uniqueItems`, `description`) is already valid JSON Schema and passes
     through unchanged.
3. Write each converted schema to its own file, adding `$schema`
   (`https://json-schema.org/draft/2020-12/schema`), `$id` (the filename,
   e.g. `AlertView.schema.json`), and `title` (the schema name).
4. Replace `components.schemas.<Name>` in the spec with a single-line
   `$ref: './schemas/<Name>.schema.json'` stub. Nothing else in the spec
   changes — `paths`, `parameters`, `requestBody`, and `responses` still
   reference `#/components/schemas/<Name>`, which now resolves through
   that stub to the external file, so the spec's operations are untouched.

### Known, pre-existing gap — don't silently "fix" it

OpenAPI 3.0 doesn't allow `nullable` as a sibling of a bare `$ref` (only
alongside `type`), so a component-typed field that's actually nullable at
runtime but expressed in the spec as a bare `$ref` (no `nullable`) won't be
marked nullable in the extracted schema either — the source spec already
lost that information. Carry it over faithfully rather than guessing which
`$ref` fields are actually nullable and injecting `type: ["null"]` siblings
that aren't backed by the source. Note the gap to the user rather than
"fixing" it during extraction.

## Verification

Do this pass yourself, unasked, before reporting done:

- Every extracted `.json` file and rewritten `.yaml` file parses.
- For every module: `paths` and `info` are byte-for-byte identical
  (semantically) to the source spec; the extracted schema names match the
  source's `components.schemas` keys exactly; every stub is exactly
  `{$ref: './schemas/<Name>.schema.json'}`; every referenced file exists.
- Every internal `$ref` across the extracted schema files resolves to an
  existing sibling file (no dangling refs) — count them and report the
  count.
- Every schema file passes `Draft202012Validator.check_schema` (the
  `jsonschema` Python library) — each is a structurally valid JSON Schema
  document on its own.
- End-to-end instance validation on at least one non-trivial schema: load
  it into a `jsonschema` validator backed by a `referencing.Registry` over
  its sibling files (so `$ref`s resolve across file boundaries, the way a
  real consumer would use these), validate a realistic instance (should
  pass), then mutate one enum/required field to an invalid value (should
  be rejected) — confirms the cross-file `$ref` graph actually works, not
  just that each file is independently well-formed.

If anything doesn't match, fix the script/output — don't report the
mismatch instead of fixing it.

## Reporting

After writing and verifying, summarize per module: schema count extracted,
output path, and total `$ref` count. Don't paste full schema contents into
the response — point at the files.
