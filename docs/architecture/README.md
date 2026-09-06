# Architecture Index

This directory indexes architecture-relevant feature work and the implementation plans that define cross-layer contracts.

| Feature | Architecture focus | Implementation plan | Status |
| --- | --- | --- | --- |
| Medication Detail + pre-alarm dose decisions | Stable dose-slot identity, explicit occurrence identity, transactional dose decisions, decision-aware AlarmManager scheduling and receiver guards | [`../feature-plans/medication-detail-dose-actions/README.md`](../feature-plans/medication-detail-dose-actions/README.md) | Planned / branch active |

## Current feature contract

The medication-detail feature deliberately keeps the existing Room schema and editor architecture. Its correctness boundary is the persisted dose decision: AlarmManager state may be cancelled/rescheduled, but reminder delivery must always consult persisted decision state before displaying a reminder.

For implementation sequencing, invariants, race handling, and verification scenarios, use the linked feature plan as the source of truth until the feature is merged and the stable architecture is folded into permanent architecture documentation.
