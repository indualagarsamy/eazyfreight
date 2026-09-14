---
name: openapi-wire-controller
description: Wire an existing Spring controller to implement its generated OpenAPI `<Tag>Api` interface (produced by the openapi-generate-server-jar skill), adding a `<Feature>ApiMapper` that bridges domain/service DTOs to the generated model classes. Use when asked to wire/connect a controller to its generated interface, enforce an OpenAPI spec as the controller contract, or add an ApiMapper for a feature.
---

# Wire a controller to its generated OpenAPI interface

Makes the hand-authored spec the enforced contract: instead of the
controller declaring its own `@GetMapping`/`@PostMapping` routes, it
`implements` the generated `<Tag>Api` interface (from
`openapi-generate-server-jar`), so a controller that drifts from its spec
fails to compile rather than silently diverging from undocumented behavior.

Precondition: the feature's jar is already built and on the classpath (see
`openapi-generate-server-jar`), and `./gradlew compileJava` succeeds with it
present.

## 1. Find the generated interface's real name and method signatures

Don't assume `<Feature>Api` — `useTags=true` names it after the spec's
`tags:` value, which may not match the feature name exactly (e.g. a
`tags: [Alerts]` spec generates `AlertsApi`, not `AlertApi`). Extract the
interface source from the jar (it was packaged with both `.class` and
`.java`) or from the generation output directory, and read every method
signature — return type, parameter types/order, parameter names — before
writing the controller. The `@Override` methods must match these exactly.

## 2. Rewrite the controller class

- Keep only the class-level `@RestController` and
  `@RequestMapping("/api/...")` — remove every method-level
  `@GetMapping`/`@PostMapping`/`@PutMapping` annotation. The interface's
  default methods already carry `@RequestMapping`, and Spring merges
  annotations from implemented interfaces, so per-method annotations on the
  implementation are redundant (and one more place to drift from the spec).
- Add `implements <Interface>` to the class declaration.
- Add `@Override` to every method the interface declares, matching its
  signature exactly. Delegate to the existing service layer inside each.
- Preserve the response status the spec calls for: creation endpoints
  (mostly `201`, per the generated interface's javadoc) use
  `ResponseEntity.status(HttpStatus.CREATED)`; everything else uses
  `ResponseEntity.ok(...)`. Don't silently "fix" a status that looks
  inconsistent with a sibling endpoint that shares implementation (e.g. a
  PDF-render path reused by two operations with different documented
  statuses) — keep the existing behavior and flag the drift instead of
  changing it unasked.
- If the interface renamed a method relative to the old hand-written
  controller (generated names tend to be more verbose/explicit, e.g.
  `getById` → `getFilingById`), just rename to match — the interface name
  wins, it's the contract now.

## 3. Add a `<Feature>ApiMapper`

Package-private, same `controller` package as the controller, non-
instantiable (private constructor). It converts between the domain/service
DTOs the service layer already uses and the generated
`com.eazyfreight.<feature>.model.*` classes:

- **Enums**: before writing any enum mapping, read both the domain enum and
  the generated model enum and confirm their constant names and counts line
  up. Once confirmed, one generic helper covers every pair:
  ```java
  private static <S extends Enum<S>, T extends Enum<T>> T mapEnum(S source, Class<T> targetType) {
      return source == null ? null : Enum.valueOf(targetType, source.name());
  }
  ```
  For a `Set<Enum>` field, map element-by-element into a `LinkedHashSet`
  (see `AlertApiMapper.mapEnumSet` for the pattern) rather than hand-rolling
  a switch per enum pair.
- **Timestamps**: generated models use `OffsetDateTime`; domain code
  typically uses `Instant`. Convert explicitly both directions:
  ```java
  private static OffsetDateTime toOffsetDateTime(Instant instant) {
      return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
  static Instant toInstant(OffsetDateTime dateTime) {
      return dateTime == null ? null : dateTime.toInstant();
  }
  ```
  `LocalDate`/`BigDecimal` fields usually need no conversion if both the
  domain and generated model already use those types — verify per field
  rather than assuming.
- **Optional request bodies**: if the generated interface marks a request
  body optional (nullable parameter) but the service method dereferences its
  fields directly, don't let a `null` body NPE — have the mapper return an
  all-null-fields record/DTO for a `null` input instead of forwarding `null`.
- One static method per direction per DTO (`toView(domain, ...)`,
  `toDomain(generatedModel)`), named for what it returns, not "convert" or
  "map" generically.
- **Optional (non-`@NotNull`) `Boolean` fields on a generated request model**
  that back a *primitive* `boolean` in the domain/service DTO: don't unbox
  directly (`request.getSpotRate()` into a primitive `boolean` parameter) —
  a caller that omits the JSON key leaves the generated field `null`, which
  NPEs on unboxing. Default it, e.g. `value != null && value`. This is
  different from the whole-optional-body case above: it's a single field
  inside an otherwise-required body.
- Don't add a `List`-returning convenience overload distinguished only by
  its generic type parameter (e.g. `toModelList(List<Quote>)` next to
  `toModelList(List<Rate>)`) — both erase to `List`, so `javac` rejects it
  as a name clash. Inline `.stream().map(Mapper::toModel).toList()` at each
  call site instead (see `AlertController`'s query methods for the
  pattern) rather than introducing a per-type list wrapper.

## 4. Clean up superseded hand-rolled DTOs

If a feature had its own request/response DTO classes purely for the old
controller (e.g. an `<Feature>Requests`/`<Feature>Responses` grouping file)
and the controller was their only caller, delete them once the mapper
replaces that logic — grep the whole project for other callers first. Don't
delete a DTO that the service layer itself still takes/returns as its
public type; only the controller-boundary translation moves into the
mapper, the service layer's own types are unaffected.

## 5. Verify

- Compile the single module first if iterating on one feature.
- Always finish with a whole-project check: `./gradlew clean compileJava`.
  A signature mismatch with the generated interface is a compile error, not
  a runtime surprise — treat any compile failure here as the mapper or
  controller being wrong, not the generated interface.

## Doing this across multiple controllers

Wire one controller by hand first, end-to-end (interface + mapper +
`./gradlew compileJava` passing), to confirm the pattern for this codebase
before repeating it elsewhere. Once that reference implementation compiles
clean, the remaining controllers can be wired concurrently (one pass per
module) against it, since each module's interface/mapper/controller triad
is independent of the others.
