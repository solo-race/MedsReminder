# Review Archive

This directory is the canonical archive for repository architecture, feature, implementation, and regression reviews.

## Default read rule

**Always read the latest review before making architecture or feature implementation decisions.**

Latest review: [`2026-09-08-0231-sgt-phase-5-medication-detail-ui-corrective.md`](2026-09-08-0231-sgt-phase-5-medication-detail-ui-corrective.md)

The `Latest review` pointer above is authoritative. Every new review must update this pointer in the same change that adds the review document.

## Archive rules

1. Every review is stored as a new Markdown file in this directory; do not overwrite a previous review with a later review.
2. Review filenames and H1 titles are locked to the time the review is performed in Singapore time (`Asia/Singapore`, SGT, UTC+08:00).
3. Filename format: `YYYY-MM-DD-HHmm-sgt-<review-scope>.md`.
4. H1 format: `# YYYY-MM-DD HH:mm SGT — <Review scope>`.
5. Once created, an archived review keeps its timestamped filename and H1. Corrections or follow-up findings go into a new review document rather than retitling an earlier one.
6. Each review should identify the reviewed branch/ref or commit, scope, findings ordered by severity, architecture implications, test implications, and disposition.
7. `docs/architecture/README.md` indexes this archive and must direct readers to this file for the current latest-review pointer.

## Review index

| Review time | Scope | Branch / base | Disposition |
| --- | --- | --- | --- |
| 2026-09-08 02:31 SGT | Phase 5 — Medication Detail UI corrective implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `823bb68e` | PASS WITH NON-BLOCKING FINDINGS; Phase 5 gate passed |
| 2026-09-08 01:28 SGT | Phase 5 — Medication Detail UI implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `27fd89f2` | CHANGES REQUIRED; Phase 5 gate not passed |
| 2026-09-07 22:12 SGT | Phase 4 — Decision-aware Scheduling corrective implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `76820848` | PASS WITH NON-BLOCKING FINDINGS; Phase 4 gate passed |
| 2026-09-07 08:01 SGT | Phase 4 — Decision-aware Scheduling implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `5e87d632` (current HEAD `fa745bcf`, docs-only after implementation) | CHANGES REQUIRED; Phase 4 gate not passed |
| 2026-09-07 06:58 SGT | Phase 3 — Transactional Dose Decision implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `6c9f92fe` | PASS WITH NON-BLOCKING FINDINGS; Phase 3 gate passed |
| 2026-09-07 06:24 SGT | Phase 2 — Dose Occurrence Contract corrective implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `57697841` | PASS WITH NON-BLOCKING FINDINGS; Phase 2 gate passed |
| 2026-09-07 04:57 SGT | Phase 2 — Dose Occurrence Contract implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `ffe265da` | CHANGES REQUIRED; Phase 2 gate not passed |
| 2026-09-07 01:42 SGT | Phase 1 — Stable Dose-Time Identity implementation Review Gate | `codex/feature-medication-detail-dose-actions` @ `a338931e` | PASS WITH NON-BLOCKING FINDINGS; Phase 1 gate passed |
| 2026-09-07 00:21 SGT | Medication Detail + pre-alarm dose decisions architecture/implementation review | `codex/feature-medication-detail-dose-actions`, based on `master` `ebafa559` | Plan revised; implementation prerequisites identified |
