---
name: spring-openapi
description: Derive an OpenAPI 3.0 spec (paths, schemas, parameters) from this project's Spring Boot controllers and DTOs. Use when asked to generate/write an OpenAPI or Swagger spec, document the API, or produce a schema for a controller/endpoint.
---

# OpenAPI spec derivation

There is no springdoc/springfox dependency in this project (see `build.gradle`)
and no `/v3/api-docs` endpoint, so the spec has to be derived by reading source
— controllers for paths/operations, DTOs for schemas.

## 1. Find, list, and scope the controllers

See `../spring-controllers/references/discover-controllers.md` for how to
locate controller files and extract their HTTP verb + path + params. Follow
that first; the rest of this skill covers turning what it finds into OpenAPI.

Before writing any YAML:
- Run the discovery grep/find and enumerate every match — one line each with
  the file path, the controller class name, and its base `@RequestMapping`
  path. This list is the build plan; don't skip straight to generating from
  memory or from controllers named earlier in the conversation.
- State that list (briefly) before generating, so it's clear which
  controllers are in scope.
- Every controller on that list gets a spec built for it per steps 2–4 below
  — not just the ones the user happened to name. See Output for how the
  user's ask narrows or doesn't narrow that list.

## 2. Map a handler method to an OpenAPI operation

- `@RequestMapping` (class) + `@GetMapping`/`@PostMapping`/etc. (method) → the
  path key and HTTP verb. Combine class-level and method-level path segments.
- `@PathVariable` → a `path` parameter, always `required: true`. Its Java type
  maps to an OpenAPI type (`UUID`/`String` → `string`, `int`/`long` → `integer`,
  enum → `string` with an `enum:` list of its constants).
- `@RequestParam` → a `query` parameter. `required` is `false` only if the
  annotation sets `required = false` or has a `defaultValue`; otherwise `true`.
  A `defaultValue` becomes the schema's `default`.
- `@RequestHeader` → a `header` parameter. In this repo it's almost always the
  `X-Actor` header with a `defaultValue`, so mark it optional and give it a
  short description ("actor recorded in the audit trail").
- `@RequestBody`/`@Valid @RequestBody <Type> request` → `requestBody`, `required`
  matching whether the annotation is `@RequestBody(required = false)`, with
  `content: application/json` and a `$ref` to the schema derived from `<Type>`
  (see step 3).
- Return type → the success response schema:
  - `ResponseEntity<X>` and bare `X` both resolve to `X`.
  - `List<X>` → `type: array, items: $ref X`.
  - `ResponseEntity<Resource>` with `produces = MediaType.APPLICATION_PDF_VALUE`
    → response content type `application/pdf`, schema `type: string, format: binary`,
    not a JSON schema.
  - `Map<String, Object>` → `type: object` with no fixed properties (or inline
    the observed keys from the method body if it's a small fixed shape, e.g.
    `AlertController.evaluate()`'s `{changes, open}`).
- Status code:
  - Default `200` for a plain return type.
  - `ResponseEntity.status(HttpStatus.CREATED)` in the method body → `201`.
  - No explicit error responses are declared per-endpoint in this repo; don't
    invent 4xx/5xx schemas unless the user asks — note that errors go through
    `GlobalExceptionHandler` if you need to describe error shape.

## 3. Derive a schema from a DTO

Request/response DTOs are Java records in `<feature>/dto`. For each record
component:
- Java type → OpenAPI type (`String`→`string`, `UUID`→`string, format: uuid`,
  `Instant`→`string, format: date-time`, `LocalDate`→`string, format: date`,
  `BigDecimal`→`number`, `boolean`/`Boolean`→`boolean`, `int`/`long`→`integer`,
  a domain enum → `string, enum: [...]` from that enum's constants, a nested
  record or `List<NestedRecord>` → `$ref`/array of `$ref` to that record's own
  derived schema).
- Jakarta Bean Validation annotations on a component add to `required` and
  constraints: `@NotNull`/`@NotBlank`/`@NotEmpty` → field goes in the schema's
  `required` list; `@NotBlank`/`@NotEmpty` also implies `minLength: 1`;
  `@Size(min=, max=)` → `minLength`/`maxLength` (strings) or `minItems`/
  `maxItems` (lists); `@Min`/`@Max` → `minimum`/`maximum`; `@Email` →
  `format: email`. A component with no such annotation is optional.
- `@Valid` on a nested field means the nested schema's own constraints apply
  too — resolve it recursively rather than treating it as opaque.
- A primitive field (`int`, `long`, `boolean`, not the boxed type) can't be
  `null`, so it will never carry `@NotNull` — but a `@Min`/`@Max`/`@DecimalMin`
  on one still means the field is meaningful and expected. Add primitive
  fields with such a constraint to `required` too, not just boxed/reference
  fields carrying `@NotNull`/`@NotBlank`/`@NotEmpty`.
- Domain enums live in `<feature>/domain`; read the enum file to list its
  constants for the `enum:` array.

Response DTOs (e.g. `QuoteResponse`) generally have no validation annotations —
their fields are just typed as above, none marked required unless the caller
says otherwise.

## Output

Default to OpenAPI 3.0.3 YAML. Build a spec for every controller on the
step-1 list, scoped by what the user asked for:
- One controller/feature named explicitly → narrow the step-1 list to just
  that controller.
- Anything broader — "the whole API", "all the controllers", "document the
  API", or no controller named at all — don't narrow the list at all: build
  every controller step 1 found, not just the ones already discussed in the
  conversation or the first few found.
- When in doubt about which case applies, don't narrow: a missing controller
  in the output is a worse failure than an unrequested one.

Always write one spec file per controller, never a combined multi-controller
file — even when the ask is "the whole API" or "all the controllers". Each
controller's spec covers just its own paths and the schemas its DTOs
reference, transitively. If a controller's paths/DTOs don't fit the patterns
in steps 2–3 (an unusual return type, a missing DTO, etc.), still include it
in its own spec — note the irregularity inline as a YAML comment rather than
dropping the operation.

Write every generated spec to a file under `./specs` (relative to the repo
root), creating the directory if it doesn't exist — never write a spec
inline-only or to the repo root/elsewhere. Name each file after its feature,
kebab-case, `.yaml` extension: `specs/<feature>-openapi.yaml`, e.g.
`../../../docs/sample_specs/quote-openapi.yaml`, `../../../docs/sample_specs/booking-openapi.yaml`. A "whole API" ask
produces one such file per controller from the step-1 list, not a single
`specs/openapi.yaml`.

Redeploying/regenerating a spec overwrites its existing file at the same
path rather than creating a new one. After writing, tell the user the paths
written (and briefly summarize what's in each) rather than pasting the full
YAML into the response.

Prefer `components.schemas` with one entry per DTO record (named after the
record, e.g. `CreateBookingRequest`) referenced via
`$ref: '#/components/schemas/...'` from paths, rather than inlining schemas —
this repo has enough shared DTOs (e.g. `BookingCargoDetailRequest` reused
across requests) that inlining would duplicate them. A DTO shared across
features (e.g. `ContainerType`) gets its schema duplicated into each
controller's own spec file — each spec must stand alone.

## 4. Self-verify before presenting

Do this pass yourself, unasked, before showing or writing the spec — don't
wait for the user to request validation separately:

- Re-open the controller and, for every handler method, confirm the YAML has
  a matching path + HTTP verb + operation, with the same path/query/header
  parameters, the same `required`/`default` on each, the same request body
  presence and `required`, and the same success status code.
- For every DTO schema, list its record components (including any nested
  record) and confirm each one appears in the schema's `properties`, with no
  extras and none missing. Re-check `required` against the rules in step 3 —
  including the primitive-with-constraint case, which is easy to drop.
- If anything doesn't match, fix the YAML — don't report the mismatch instead
  of fixing it.
