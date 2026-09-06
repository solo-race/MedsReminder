# Review Archive

This directory is the canonical archive for repository architecture, feature, implementation, and regression reviews.

## Default read rule

**Always read the latest review before making architecture or feature implementation decisions.**

Latest review: [`2026-09-07-0021-sgt-medication-detail-dose-actions.md`](2026-09-07-0021-sgt-medication-detail-dose-actions.md)

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
| 2026-09-07 00:21 SGT | Medication Detail + pre-alarm dose decisions architecture/implementation review | `codex/feature-medication-detail-dose-actions`, based on `master` `ebafa559` | Plan revised; implementation prerequisites identified |
