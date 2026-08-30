# Discovering controllers in this repo

Shared by the `spring-controllers` and `spring-openapi` skills. Any change here
applies to both.

Controllers live at `src/main/java/com/eazyfreight/<feature>/controller/*.java`,
one feature package per business area (e.g. `quote`, `booking`, `compliance`,
`alerts`, `finance`, `logistics`, `documentation`).

## How to find them

1. Locate candidate files:
   ```
   grep -rl "@RestController\|@Controller" src/main/java
   ```
   (equivalently: `find src/main/java -iname "*Controller.java"`)

2. Skip `GlobalExceptionHandler` and any `@ControllerAdvice` class — those are
   cross-cutting error handlers, not resource controllers.

3. For each remaining file, read it and extract:
   - The feature package (the directory segment before `controller`, e.g. `quote`).
   - The class-level `@RequestMapping` base path, if present.
   - Each handler method: HTTP verb (`@GetMapping`/`@PostMapping`/`@PutMapping`/
     `@PatchMapping`/`@DeleteMapping`) and its path, combined with the base path.
   - The method name, its `@PathVariable`/`@RequestParam`/`@RequestBody`/
     `@RequestHeader` params, and its return type.

## Conventions worth knowing

- No PUT for state transitions — lifecycle changes are POSTs to named
  sub-resources (`/send`, `/accept`, `/{id}/submit`, etc.), not a generic
  update. Only `AlertController`'s `/configurations/{alertType}` uses PUT,
  for replacing a configuration record.
- `X-Actor` is a common `@RequestHeader` (with a `defaultValue`) carrying who
  performed the action, written into an audit trail — not a real request body
  field.
- Request/response DTOs live in `<feature>/dto` as Java records; domain enums
  live in `<feature>/domain`.
- A few endpoints return `ResponseEntity<Resource>` with
  `produces = MediaType.APPLICATION_PDF_VALUE` (PDF downloads) instead of JSON.
