# Contributing to cloud-itonami-isic-5819

Contributions should preserve the actor's scope: back-office coordination only,
with CRITICAL exclusions of finalizing editorial-content decisions and
legal-risk clearance decisions (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: clojure -M:test
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Finalize an editorial-content decision (what a card/calendar/poster design actually depicts, whether it ships as designed).
- Issue a legal-risk clearance decision (copyright/trademark/licensed-character clearance).

Contributions that cross these boundaries will be rejected.
