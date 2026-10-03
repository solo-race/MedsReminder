# Phase 5 — Medication Detail UI

## Objective

Add a read-only medication detail destination without duplicating editor state or save/delete logic.

## Navigation

- Add `DETAIL = medication/{medicationId}` and a helper route builder.
- Today card → Detail.
- Reminder content intent → Detail, preserving explicit occurrence extras.
- Detail Edit button → existing `EditMedicationScreen`.
- Existing add/new flow remains unchanged.

## Detail content

Display existing medication information only:

- name/alias as appropriate;
- dosage;
- note;
- photo;
- weekdays and times;
- time-zone mode/zone;
- relevant current/upcoming occurrence and its scheduled time.

## Actions

- Taken
- Skipped
- Edit

Taken/Skipped call the shared dose-decision use-case through the ViewModel. They must be disabled when no valid actionable occurrence exists or when that occurrence has already been decided.

## State rules

1. An explicit notification occurrence wins over a newly calculated upcoming occurrence.
2. Home-opened Detail derives the next actionable upcoming occurrence from the current plan.
3. After a decision, UI reflects the decision and no longer offers a second conflicting action for that occurrence.
4. Missing/deleted medication is handled without navigating into an editor with null data.
5. Detail must not own editable copies of medication fields.

## Localization

Add semantic English and Simplified Chinese strings for Detail title, schedule labels, next/current dose copy, Taken/Skipped action states, stale/no-actionable-dose messaging, and Edit.

## Tests

- Home card routes to Detail rather than Edit.
- Notification route resolves the target medication and occurrence.
- Edit button routes to the existing editor.
- Taken/Skipped invoke the same decision path.
- Stale occurrence disables dose actions.

## Exit criteria

Detail is a read-only presentation/action surface; all mutation semantics remain in existing editor logic or the shared dose-decision business layer.
