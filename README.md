# cloud-itonami-isic-5819

**Other publishing activities** — ISIC Rev.4 class 5819.

A coordination-only actor for the ISIC 5819 residual "other publishing" category, behind an independent Governor that earns advisor trust through structured oversight: proposal → advise → govern → decide → commit|hold|escalate.

## Concrete illustrative product line

ISIC 5819 covers publishing activities of catalogs, greeting cards, calendars, posters, advertising material, forms, and other printed matter not elsewhere classified (i.e. not books [5811], newspapers [5813], directories/mailing lists [5812], or software [5820]). This repository's concrete illustration, documented plainly per this fleet's fresh-scaffold convention, is a **specialty publisher producing greeting cards, calendars, and posters**. The domain shape generalizes directly to catalogs and forms publishing as well — see `specpubops.store` docstring.

## Features

- **Closed proposal-op allowlist**: log-production-record, schedule-production-operation, coordinate-distribution, flag-content-concern (all `:effect :propose`).
- **Three HARD governor checks** (permanent, un-overridable):
  1. **Title verified** — target title (a card/calendar/poster/catalog title in production) must exist AND be registered/verified in the store before any proposal for it may commit or escalate.
  2. **Effect is :propose** — any other `:effect` value is rejected.
  3. **Scope exclusion** — finalizing an editorial-content decision or a legal-risk clearance decision (copyright/trademark/licensed-character clearance) is permanently blocked, regardless of confidence or op.
- **Staged rollout** (Phase 0→3):
  - Phase 0: read-only
  - Phase 1: production-record logging only (approval-gated)
  - Phase 2: + production-operation scheduling, distribution coordination (approval-gated)
  - Phase 3: auto-commits clean, high-confidence proposals (content concerns always escalate)
- **Append-only audit ledger** — every decision is an immutable log entry.
- **langgraph-clj StateGraph** — one request = one supervised run; human-in-the-loop via `interrupt-before`.

## CRITICAL scope exclusions

This actor coordinates the back-office operations of a specialty publisher:
design/print-run production-record logging, design/proofing/print-run
scheduling proposals, outbound distribution coordination (to retailers/
wholesalers), and copyright/content-risk-concern flagging (trademark or
licensed-character similarity, cultural/seasonal sensitivity, plagiarism
of another publisher's design).

**This actor does NOT:**
- Finalize an editorial-content decision (what a card/calendar/poster design actually depicts, whether it ships as designed).
- Issue a legal-risk clearance decision (copyright/trademark/licensed-character clearance).

Every proposal is `:effect :propose` only. `:flag-content-concern` always
escalates to a human, at every phase, regardless of confidence — this
actor never self-clears a content-risk concern it raises (ADR-2607152500
Wave-4 person-facing-service safety guardrail: the closed op allowlist
never includes an op that directly finalizes an editorial-content
decision or a legal-risk clearance, and any "flag a concern" op always
escalates and is never auto-commit-eligible).

## Development

```bash
# Install dependencies (if inside the superproject, use :dev alias for local overrides)
clojure -M:dev -P

# Run tests
clojure -M:dev:test

# Run linter
clojure -M:lint

# Run demo
clojure -M:run
```

## Test suite

- `test/specpubops/governor_test.cljk` — unit tests of governor hard checks and scope exclusion
- `test/specpubops/advisor_test.cljk` — advisor proposal shape and consistency (includes a dedicated regression test guarding against the default mock-advisor ever self-tripping the scope-exclusion check)
- `test/specpubops/phase_test.cljk` — rollout phase logic
- `test/specpubops/governor_contract_test.cljk` — full graph integration, audit trail
- `test/specpubops/store_contract_test.cljk` — Store protocol and MemStore implementation

## Modules

- `specpubops.store` — SSoT (MemStore, String-keyed title directory, append-only ledger)
- `specpubops.advisor` — contained intelligence node (mock + real-LLM seam)
- `specpubops.governor` — independent compliance layer
- `specpubops.phase` — staged rollout (0→3)
- `specpubops.operation` — langgraph-clj StateGraph
- `specpubops.sim` — demo driver

## License

AGPL-3.0-or-later. See LICENSE file.

## Governance

This actor is part of the cloud-itonami Wave 4 (human-facing/personal-services) fleet. See ADR-2607121000, ADR-2607152500, and the ISIC-5819 coverage ADR in the `com-junkawasaki/root` superproject for design decisions.
