---
name: spring-openapi
description: Derive an OpenAPI 3.0 spec (paths, schemas, parameters) from this project's Spring Boot controllers and DTOs. Use when asked to generate/write an OpenAPI or Swagger spec, document the API, or produce a schema for a controller/endpoint.
---

# OpenAPI spec derivation

There is no springdoc/springfox dependency in this project (see `build.gradle`)
and no `/v3/api-docs` endpoint, so the spec has to be derived by reading source
— controllers for paths/operations, DTOs for schemas.

## 1. Find the controllers

See `../spring-controllers/references/discover-controllers.md` for how to
locate controller files and extract their HTTP verb + path + params. Follow
that first; the rest of this skill covers turning what it finds into OpenAPI.

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

Default to OpenAPI 3.0.3 YAML. Scope to whatever the user asked for:
- One controller/feature → a spec (or fragment) covering just its paths and
  the schemas its DTOs reference, transitively.
- "the whole API" → walk every controller found in step 1.

Write the result to a file only if asked; otherwise show it inline. Prefer
`components.schemas` with one entry per DTO record (named after the record,
e.g. `CreateBookingRequest`) referenced via `$ref: '#/components/schemas/...'`
from paths, rather than inlining schemas — this repo has enough shared DTOs
(e.g. `BookingCargoDetailRequest` reused across requests) that inlining would
duplicate them.
