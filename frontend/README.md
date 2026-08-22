# Eazy Freight — Operations Console

React front end for the Eazy Freight service. Covers the **Quote** and **Booking**
contexts.

## Running it

The service must be up first:

```shell
cd ..
docker compose up -d
export JAVA_HOME=/opt/homebrew/opt/openjdk@17   # keg-only install
./gradlew bootRun                                # :8080
```

Then:

```shell
npm install
npm run dev        # http://localhost:5173
```

Vite proxies `/api` to `localhost:8080`, so the browser only ever talks to one
origin and the service needs no CORS configuration for local development. Override
the target with `VITE_API_TARGET` if the service runs elsewhere.

```shell
npm run build      # tsc -b && vite build
npm run lint
```

## Stack

React 19 · TypeScript · Vite 8 · TanStack Query 5 · React Router 7.

No CSS framework. Design tokens in `src/styles/tokens.css` with a light and dark
palette; components use CSS modules. The theme follows the browser's
`prefers-color-scheme`.

## Layout

```
src/
├── api/          typed client + one query/mutation hook per command
├── components/   shell, lifecycle stepper, timeline, toasts, gated buttons
├── pages/        list, create and detail screens per context
└── styles/       tokens and global element styles
```

## How it treats the domain

**Commands, not field edits.** Every state change is a POST to a named endpoint —
`/send`, `/accept`, `/carrier-confirmation`, `/reinstate`. There is no form that
lets you set a status directly, because the service has no endpoint that would
accept one.

**Refusals are first-class.** The service answers a rejected command with `409` and
a sentence explaining why. `ApiError.isDomainRuleViolation` separates that from a
`400` validation failure, and the toast says "Action not allowed" with the domain's
own wording rather than presenting it as a system error.

**Actions are gated with the reason shown.** `unavailableReason()` on each detail
page mirrors the aggregate's guards, so a button that cannot succeed is disabled and
explains itself on hover — "Price the quotation first", "A truck order cannot be
raised before the carrier confirms". This is a convenience, not enforcement: the
server remains the authority and anything that slips through still comes back 409.
`GatedButton` puts the reason in `title` and `aria-describedby` but never in the
accessible name, so screen-reader users still hear what the button does.

**Server validation lands on the field.** A `400` carries per-field messages keyed
by property path (`cargoDetails[0].hsCode`), which map directly onto form field
names via `useFieldErrors`.

**The audit trail is the point.** The booking detail page renders
`statusHistory` as a timeline showing what changed, when, who did it and whether it
came from a person or INTTRA — and `reinstatements` as before/after sailings, so a
rolled booking visibly keeps its reference while the vessel changes.

## Known gaps

* **No authentication.** The actor is a hard-coded `X-Actor: ops.jane` header. A
  real deployment would take it from the authenticated principal.
* **Party and carrier pickers are raw UUID fields.** Those contexts do not exist in
  the service yet, so ids are typed or defaulted to demo values.
* **No pagination.** List screens fetch everything and filter client-side, which is
  fine for a demo and wrong for real volumes.
* **Post-booking tracks are absent** — Container & Equipment, Compliance,
  Documentation, Finance and Alerts have no screens because they have no service.
* **Not tested.** There are no component or end-to-end tests. Flows were verified
  by hand against a live service.
* **Dev-proxy only.** A production deployment needs either same-origin serving or a
  CORS policy on the service.
