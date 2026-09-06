# Architecture Index

This directory indexes architecture-relevant feature work and the implementation plans that define cross-layer contracts.

## Default reading order

Before making architecture or feature implementation decisions, read the repository review archive first: [`../review/README.md`](../review/README.md). Its **Latest review** pointer is the authoritative entry point to the most recent review and must be read by default before relying on older plans or assumptions.

After the latest review, use this architecture index and then the relevant feature implementation plan.

## Architecture and feature index

| Feature / source | Architecture focus | Implementation plan / entry point | Status |
| --- | --- | --- | --- |
| Review archive | Time-locked architecture, implementation, and regression reviews; latest findings override older review assumptions when they conflict | [`../review/README.md`](../review/README.md) — read **Latest review** first | Active / canonical review entry point |
| Medication Detail + pre-alarm dose decisions | Stable dose-slot identity, explicit occurrence identity, transactional dose decisions, decision-aware AlarmManager scheduling and receiver guards | [`../feature-plans/medication-detail-dose-actions/README.md`](../feature-plans/medication-detail-dose-actions/README.md) | Planned / branch active |

## Current feature contract

The medication-detail feature deliberately keeps the existing Room schema and editor architecture. Its correctness boundary is the persisted dose decision: AlarmManager state may be cancelled/rescheduled, but reminder delivery must always consult persisted decision state before displaying a reminder.

For current findings and corrections to assumptions, read the latest archived review first. For implementation sequencing, invariants, race handling, and verification scenarios, then use the linked feature plan as the working source of truth until the feature is merged and the stable architecture is folded into permanent architecture documentation.
