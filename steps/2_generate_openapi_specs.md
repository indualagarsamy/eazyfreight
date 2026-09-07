# Generate OpenAPI Specs

OpenAPI 3.0.3 specs derived from the controllers and DTOs identified in
[`1_identify_endpoints.md`](./1_identify_endpoints.md). There is no
springdoc/springfox dependency in this project and no `/v3/api-docs`
endpoint, so each spec was hand-derived by reading controller source for
paths/operations and DTO records (plus their Jakarta Bean Validation
annotations and domain enums) for schemas.

One spec per controller, written to
[`2_generate_openapi_specs_output`](2_generate_openapi_specs_output), never combined
into a single file — each spec stands alone, duplicating any cross-feature
type it needs (e.g. `ContainerType`) rather than referencing another file.

## quote.yaml — QuoteController (`/api/quotes`)
`2_generate_openapi_specs_output/quote.yaml`

- 14 operations (7 GET, 7 POST), one per handler method.
- 18 schemas: 7 enums (`ShippingMode`, `QuoteStatus`, `ScreeningStatus`, `ChargeUnit`, `QuoteLineType`, `RateType`, `SurchargeType`) + 11 request/response DTOs (`CreateQuoteRequest`, `AcceptQuoteRequest`, `DeclineQuoteRequest`, `BuildQuotationRequest`, `CargoDetailRequest`/`Response`, `QuoteLineRequest`/`Response`, `QuoteResponse`, `RateResponse`, `SurchargeResponse`).
- `POST /api/quotes` returns `201`; every other operation defaults to `200`.

## booking.yaml — BookingController (`/api/bookings`)
`2_generate_openapi_specs_output/booking.yaml`

- 20 operations (8 GET, 12 POST), one per handler method.
- 19 schemas: 6 enums (`BookingStatus`, `ShippingMode`, `CancellationInitiator`, `StatusChangeSource`, `BookingSourceType`, `ContainerType`) + 13 request/response DTOs.
- `X-Actor` header present on nearly every command endpoint, optional with a default of `operations`.
- `POST /api/bookings` returns `201`; lifecycle transitions (`/submit`, `/cancel`, `/reinstate`, etc.) return `200`.

## compliance.yaml — ComplianceController (`/api/compliance`)
`2_generate_openapi_specs_output/compliance.yaml`

- 15 operations (6 GET, 9 POST), one per handler method.
- 13 schemas: 4 enums (`FilingStatus`, `FilingType`, `ScheduleBCode`, and the ITN-related enum) + 9 request/response DTOs.
- `GET /filing-system` returns a fixed-shape `Map<String, Object>` (`simulated`, `notice`), modeled as an inline object schema rather than a DTO `$ref` since it isn't backed by a record.
- Filing-creation endpoints (`initiate`, `correct`, `amend`) return `201`; the rest return `200`.

## documentation.yaml — DocumentationController (`/api/documentation`)
`2_generate_openapi_specs_output/documentation.yaml`

- 22 paths / 24 operations (two paths — `bookings/{bookingId}/instructions` and `house-bols/{id}/pdf` — each carry both a GET and a POST).
- 24 schemas: 7 enums (from `DocumentationEnums`) + 11 request DTOs + 6 response DTOs (all from the grouped `DocumentationRequests`/`DocumentationResponses` record files).
- The two PDF endpoints (`POST`/`GET .../house-bols/{id}/pdf`) use `content: application/pdf` with `type: string, format: binary` instead of a JSON schema.
- Document-creation commands (`compile`, `resend-corrected`, `master-bol`, `house-bol`, `amend`) return `201`; everything else returns `200`.

## finance.yaml — FinanceController (`/api/finance`)
`2_generate_openapi_specs_output/finance.yaml`

- 25 operations (9 GET including one PDF download, 16 POST), one per handler method.
- 24 schemas: 7 enums (from `FinanceEnums`) + 11 request DTOs + 6 response DTOs (from the grouped `FinanceRequests`/`FinanceResponses` record files).
- `Line`/`Payment` DTOs existed on both the request and response side under the same simple name (`FinanceRequests.Line` vs `FinanceResponses.Line`); renamed to `InvoiceLineRequest`/`InvoiceLineResponse` and `PaymentResponse` in the spec to avoid a schema name collision — noted as a deviation from the raw Java type names.
- `GET /invoices/{id}/pdf` uses `content: application/pdf` binary response instead of JSON.
- Creation endpoints (`prepare` invoice, `credit-note`, `storage-fee`, `storage-fee/invoice`, `credit-hold`) return `201`; the rest return `200`.

## logistics.yaml — LogisticsController (`/api/logistics`)
`2_generate_openapi_specs_output/logistics.yaml`

- 22 operations (6 GET, 16 POST), one per handler method.
- 26 schemas: 9 enums (`ContainerType`, `ContainerSource`, `SealSource`, `SealDeactivationReason`, `MovementType`, `AddressType`, `DispatchStatus`, `LogisticsStage`, `ExaminationResult`) + 9 request DTOs + 7 response DTOs + 1 inline fixed-shape schema for the ITN-gate check.
- The request DTO `ActualCargo` was renamed to `ActualCargoRequest` in the spec to avoid colliding with the response schema of the same name — noted as a deviation from the raw Java type name.
- `GET /bookings/{bookingId}/itn-gate` returns a fixed-shape `Map<String, Object>` (`clear`, `reason`), modeled inline.
- `outbound-dispatch` and `inbound-dispatch` return `201`; the rest return `200`.

## alerts.yaml — AlertController (`/api/alerts`)
`2_generate_openapi_specs_output/alerts.yaml`

- 12 operations (6 GET, 5 POST, 1 PUT), one per handler method.
- 18 schemas: 8 enums (including the 31-value `AlertType`) + 5 response DTOs + 3 request DTOs + 2 inline fixed-shape schemas for `evaluate`/`evaluate-booking` results (`{changes, open}` / `{changes}`).
- `PUT /configurations/{alertType}` is the only PUT in the entire API (see notes in step 1); every other state change is a POST.
- All operations default to `200` — no `201`s in this controller.

## Method

For each controller:
1. Mapped every `@GetMapping`/`@PostMapping`/`@PutMapping` handler to a path + operation, combining the class-level `@RequestMapping` with the method-level path.
2. Converted `@PathVariable` → required path params, `@RequestParam` → query params (optional only where `required = false` or a `defaultValue` is set), `@RequestHeader` → the optional `X-Actor` header.
3. Resolved `@RequestBody`/`@Valid @RequestBody` into a `requestBody` referencing a `components.schemas` entry derived from the DTO record, including Jakarta validation constraints (`@NotNull`/`@NotBlank`/`@NotEmpty` → `required`, `@Size`/`@Min`/`@Max` → length/range constraints, including the primitive-with-constraint case where a non-boxed field like `int pieces` still belongs in `required` because of a `@Min`/`@Max` annotation).
4. Read each referenced domain enum for its constants to populate `enum:` lists.
5. Set success status codes from the method body (`201` where `ResponseEntity.status(HttpStatus.CREATED)` appears, `200` otherwise) and modeled the PDF-download endpoints as binary `application/pdf` responses rather than JSON.
6. Self-verified every spec against its controller and DTOs before finalizing — re-checking each operation's params/body/status and each schema's properties/`required` list.

Generation was parallelized: one agent per controller, run concurrently, each producing and self-verifying its own file independently.

## Totals

| Spec | Operations | Schemas |
|---|---|---|
| quote.yaml | 14 | 18 |
| booking.yaml | 20 | 19 |
| compliance.yaml | 15 | 13 |
| documentation.yaml | 24 | 24 |
| finance.yaml | 25 | 24 |
| logistics.yaml | 22 | 26 |
| alerts.yaml | 12 | 18 |

**Total: 7 spec files, 132 operations** — matching the endpoint count from step 1 exactly.

## Java server component generation

Each spec was also compiled into a Java server component using
[OpenAPI Generator](https://openapi-generator.tech) 7.9.0 (the CLI jar,
downloaded from Maven Central — there's no springdoc/openapi-generator
Gradle plugin wired into this project), generator `spring`, with:

- `interfaceOnly=true` — only API interfaces + models, no controller
  implementations or `Application` bootstrap class.
- `useSpringBoot3=true` (implies Jakarta EE namespaces) to match this
  project's Spring Boot 3.3.2 / Java 21 stack.
- `annotationLibrary=none`, `documentationProvider=none` — no springdoc/
  swagger-annotations dependency required to compile the output.
- `useTags=true`, `dateLibrary=java8`, `openApiNullable=false`.
- Per-feature Java packages: `com.eazyfreight.<feature>.api` /
  `.model` / `.invoker`.

For each feature this produced:

- `com/eazyfreight/<feature>/api/<Feature>Api.java` — one interface per
  controller, with a default method per operation (each returning `501 Not
  Implemented` until a real controller implements the interface),
  preserving the same paths/params/request bodies/status codes as the
  source controller.
- `com/eazyfreight/<feature>/model/*.java` — one class per schema
  (request/response DTOs and enums).

The generated sources were compiled with `javac` against this project's
existing dependency versions (Spring Boot 3.3.2 → Spring Framework 6.1.11,
Jackson 2.17.2, `jakarta.validation-api` 3.0.2, `jakarta.annotation-api`
2.1.1, plus the embedded Tomcat servlet API already on the classpath via
`spring-boot-starter-web`), then both the compiled `.class` files and their
`.java` sources were packaged together into one jar per spec under their
original `com.eazyfreight.<feature>.{api,model}` package layout — no
exploded directory tree is kept alongside the jars, only the jar itself,
written directly into
[`./2_generate_openapi_specs_output`](./2_generate_openapi_specs_output):

| Jar | Classes |
|---|---|
| quote.jar | 20 |
| booking.jar | 21 |
| compliance.jar | 15 |
| documentation.jar | 26 |
| finance.jar | 26 |
| logistics.jar | 28 |
| alerts.jar | 20 |

No source changes were needed to the specs themselves; two classpath gaps
surfaced during compilation and were resolved by adding jars already used
transitively elsewhere in this project: `tomcat-embed-core` (for
`jakarta.servlet.http.HttpServletResponse`, referenced by the generator's
`ApiUtil` helper) and `jackson-databind` (for `@JsonDeserialize`, emitted
on `Set`-typed fields with `uniqueItems: true`, e.g. in `alerts.yaml`'s
`AlertView`/`ConfigurationView`/`UpdateConfiguration`).

## Wiring the controllers to the generated interfaces

The seven jars above are not just a compilation sanity-check — they're
wired into the real build (`build.gradle`: `implementation
fileTree(dir: 'steps/2_generate_openapi_specs_output', include: '*.jar')`)
and every controller now `implements` its generated `<Feature>Api`
interface instead of declaring its own `@GetMapping`/`@PostMapping`
routes. The hand-authored yaml is now the enforced contract — a
controller that drifts from its spec fails to compile (wrong method
signature) rather than silently diverging from undocumented behavior.

For each module:
1. `@Override` every method the generated interface declares, keeping
   only the class-level `@RestController` + `@RequestMapping("/api/...")`
   on the controller — no per-method Spring annotations, since the
   interface's default methods already carry `@RequestMapping` and Spring
   merges annotations from implemented interfaces.
2. Added a package-private `<Feature>ApiMapper` (in the same `controller`
   package) that converts between the existing domain/service DTOs and
   the generated `com.eazyfreight.<feature>.model.*` classes: a generic
   `mapEnum(source, targetClass)` helper (`Enum.valueOf(targetType,
   source.name())`) for every enum pair, after confirming constant names
   and counts line up between the domain enum and its generated
   counterpart, plus explicit `Instant <-> OffsetDateTime` conversion for
   every timestamp field (generated models use `OffsetDateTime`;
   `LocalDate`/`BigDecimal` fields needed no conversion since both sides
   already use those types).
3. Where a generated method's javadoc specified a non-200 status (mostly
   `201` on resource-creation endpoints), preserved that status via
   `ResponseEntity.status(...)`; everything else returns
   `ResponseEntity.ok(...)`.

Wiring was parallelized the same way generation was: `AlertController` was
done first by hand to establish and verify the pattern (it compiled
clean), then one agent per remaining module replicated it concurrently
against that reference implementation.

### Per-module notes

- **alerts** — `AlertRequests`/`AlertResponses` were deleted; `AlertController`
  was their only caller and that mapping logic now lives entirely in
  `AlertApiMapper`. Every other module's DTOs remain — they're still the
  types the service layer itself takes/returns, so the mapper only
  converts at the controller boundary.
- **booking** — all 20 endpoints matched 1:1 with the existing controller
  and `BookingService`; `createBooking` returns `201`.
- **compliance** — method names in the generated interface
  (`getFilingById`, `getFilingByReference`, `getFilingsByBooking`, etc.)
  are more verbose than the old controller's (`getById`, `getByReference`)
  but map onto the same service calls; `filingSystem()` now returns a
  `FilingSystemStatus` model object instead of a raw `Map`; `initiateFiling`/
  `correctFiling`/`amendFiling` return `201`.
- **documentation** — three methods were renamed to match the interface
  (`instructions`→`instructionsForBooking`, `masterBols`→
  `masterBolsForBooking`, `houseBols`→`houseBolsForBooking`); kept
  `generatePdf` at its existing `200` even though the interface's javadoc
  says `201`, since the PDF-rendering path is shared with `downloadPdf`
  (flagged as pre-existing spec/implementation drift, not something this
  step introduced); the optional `MasterBOLCorrection` request body is
  handled null-safely.
- **finance** — `FinanceService` already returned `FinanceResponses.*`
  view types before this change, so `FinanceApiMapper` only had to bridge
  `FinanceRequests`/`FinanceResponses` to the generated models, not
  replace them; all 25 endpoints implemented.
- **logistics** — verified 9 enum pairs (`ContainerType`,
  `ContainerSource`, `DispatchStatus`, `LogisticsStage`, `MovementType`,
  `AddressType`, `ExaminationResult`, `SealSource`,
  `SealDeactivationReason`) before mapping; the generated interface marks
  `ExaminationHold`/`LoadedOnVessel` request bodies as optional even
  though the service dereferences their fields directly, so the mapper
  returns an all-null-fields record on a `null` body instead of NPE'ing;
  `dispatchOutbound`/`dispatchInbound` return `201`.
- **quote** — all 7 enum pairs (`ShippingMode`, `QuoteStatus`,
  `ScreeningStatus`, `ChargeUnit`, `QuoteLineType`, `RateType`,
  `SurchargeType`) matched by name and count; `createQuote` returns `201`.

### Totals

| Module | Endpoints wired | New mapper |
|---|---|---|
| alerts | 12 | `AlertApiMapper` |
| booking | 20 | `BookingApiMapper` |
| compliance | 15 | `ComplianceApiMapper` |
| documentation | 24 | `DocumentationApiMapper` |
| finance | 25 | `FinanceApiMapper` |
| logistics | 22 | `LogisticsApiMapper` |
| quote | 14 | `QuoteApiMapper` |

**Total: 132 endpoints**, matching the operation count from the specs
above exactly. Verified with `./gradlew clean compileJava` across the
whole project after every module was wired.
