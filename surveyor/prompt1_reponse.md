# Prompt 1 — Business Domain Classification of `./src/main`

## Method

`./src/main` was analyzed twice:

1. **Single-pass analysis** — direct inspection of the package structure under `src/main/java/com/eazyfreight/`.
2. **Multi-run consistency check** — the same classification prompt was submitted independently to 5 fresh agents (no shared context between them) to measure how deterministic/stable the confidence scores are.

## Single-pass response

The codebase's package structure maps directly onto the requested classification — each domain has its own full vertical slice (controller/domain/dto/event/listener/repository/service).

```json
{
  "domains": [
    {
      "code": "07",
      "name": "Quote",
      "confidence": 98
    },
    {
      "code": "02",
      "name": "Booking",
      "confidence": 98
    },
    {
      "code": "03",
      "name": "Compliance",
      "confidence": 97
    },
    {
      "code": "01",
      "name": "Alerts",
      "confidence": 97
    },
    {
      "code": "06",
      "name": "Logistics",
      "confidence": 96
    },
    {
      "code": "05",
      "name": "Finance",
      "confidence": 95
    },
    {
      "code": "04",
      "name": "Documentation",
      "confidence": 94
    }
  ]
}
```

**Basis:** `src/main/java/com/eazyfreight/` contains exactly seven top-level packages — `alerts`, `booking`, `compliance`, `documentation`, `finance`, `logistics`, `quote` — each a self-contained vertical slice (controller/domain/dto/event/listener/repository/service), matching the given classification 1:1.

File counts per domain:

| Domain        | Java files |
|---------------|------------|
| alerts        | 26         |
| booking       | 30         |
| compliance    | 28         |
| documentation | 17         |
| finance       | 19         |
| logistics     | 22         |
| quote         | 35         |

Quote (35 files) and Booking (30) are the largest slices; Documentation (17) and Finance (19) are the smallest, hence the slightly lower confidence on those two.

## Multi-run consistency check (5 independent runs)

| Code | Domain        | Run1 | Run2 | Run3 | Run4 | Run5 | Avg  | Min | Max | StdDev |
|------|---------------|------|------|------|------|------|------|-----|-----|--------|
| 01   | Alerts        | 95   | 95   | 95   | 90   | 95   | 94.0 | 90  | 95  | 2.0    |
| 02   | Booking       | 95   | 97   | 95   | 92   | 97   | 95.2 | 92  | 97  | 1.8    |
| 03   | Compliance    | 95   | 95   | 95   | 90   | 96   | 94.2 | 90  | 96  | 2.1    |
| 04   | Documentation | 95   | 95   | 95   | 88   | 93   | 93.2 | 88  | 95  | 2.7    |
| 05   | Finance       | 95   | 95   | 95   | 88   | 94   | 93.4 | 88  | 95  | 2.7    |
| 06   | Logistics     | 95   | 95   | 95   | 92   | 95   | 94.4 | 92  | 95  | 1.2    |
| 07   | Quote         | 95   | 95   | 95   | 90   | 97   | 94.4 | 90  | 97  | 2.3    |

Aggregated JSON:

```json
{
  "domains": [
    { "code": "01", "name": "Alerts", "confidence_avg": 94.0, "confidence_min": 90, "confidence_max": 95, "stddev": 2.0 },
    { "code": "02", "name": "Booking", "confidence_avg": 95.2, "confidence_min": 92, "confidence_max": 97, "stddev": 1.8 },
    { "code": "03", "name": "Compliance", "confidence_avg": 94.2, "confidence_min": 90, "confidence_max": 96, "stddev": 2.1 },
    { "code": "04", "name": "Documentation", "confidence_avg": 93.2, "confidence_min": 88, "confidence_max": 95, "stddev": 2.7 },
    { "code": "05", "name": "Finance", "confidence_avg": 93.4, "confidence_min": 88, "confidence_max": 95, "stddev": 2.7 },
    { "code": "06", "name": "Logistics", "confidence_avg": 94.4, "confidence_min": 92, "confidence_max": 95, "stddev": 1.2 },
    { "code": "07", "name": "Quote", "confidence_avg": 94.4, "confidence_min": 90, "confidence_max": 97, "stddev": 2.3 }
  ]
}
```

### Takeaways

- All 7 domains were identified in every one of the 5 runs — no run dropped or added a domain, confirming the classification is stable/deterministic at the domain-presence level.
- Confidence scores cluster tightly (std dev 1.2–2.7 across all domains), since the package layout maps 1:1 to the classification (`alerts`, `booking`, `compliance`, `documentation`, `finance`, `logistics`, `quote`, each with a full controller/domain/dto/event/repository/service structure).
- Run 4 was a consistent outlier, scoring every domain ~4–7 points lower than the other 4 runs — otherwise variance is minimal.
- Booking and Quote score highest on average (95.2 and 94.4, largest packages at 30 and 35 files); Documentation and Finance score lowest (93.2 and 93.4, smallest packages at 17 and 19 files).

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)
