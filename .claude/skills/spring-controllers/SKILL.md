---
name: spring-controllers
description: Find and summarize Spring Boot REST controllers in this project (eazyfreight). Use when asked to list controllers, list API endpoints, find which controller handles a route, or audit REST mappings.
---

# Spring Boot controller discovery

See `references/discover-controllers.md` for how to locate controller files in
this repo and extract their mappings — that file is shared with the
`spring-openapi` skill, so update it in place rather than re-deriving the same
steps here.

## Output format

When asked to list/summarize controllers, report one section per feature, e.g.:

```
## quote — QuoteController (/api/quotes)
GET    /api/quotes
GET    /api/quotes/{id}
POST   /api/quotes
POST   /api/quotes/{id}/accept
...
```

Include the file path (`file:line`) for each controller class so the user can jump to it.

When asked to find "which controller handles X", grep for the path fragment across
`src/main/java/**/controller/*.java` and report the exact method + file:line.
