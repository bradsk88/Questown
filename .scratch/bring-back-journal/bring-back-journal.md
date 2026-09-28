---
title: "Bring back the in-game journal (Patchouli book) after the NeoForge 1.20.6 migration"
status: needs-triage
created: 2026-09-23
priority: p2
---

## Context

The NeoForge 1.20.6 migration drops the Patchouli dependency (the in-game
"journal"/book). The developer prefers the chicken + onboarding quests as the
player-learning path and asked to drop the journal from this migration and
return to it in a later session. Record it here so the feature is not lost.

Pre-drop, Questown touched Patchouli in 4 files:
- `gui/QuestsScreen.java`
- `gui/PagedCardScreen.java`
- `jobs/ServerJobsRegistry.java`
- `core/advancements/AdvancementEvents.java`

(`QuestsScreen` / `PagedCardScreen` are built on `PatchouliAPI` / `TriPredicate`.)

## Open questions

- Re-add the same Patchouli book (NeoForge build), or build a homegrown guide screen now that Patchouli is out of the stack?
- Which content goes in it (jobs / quests / economy / recipe hints)?
- Does the `ServerJobsRegistry` / `AdvancementEvents` Patchouli hook get the in-game book only, or does it also drive something the chicken/quests path already covers (i.e. can those hooks just go)?

## Notes

- Deferred by explicit developer decision on 2026-09-23 (chicken + onboarding
  quests are the preferred learning mechanism; journal is a "later" nicety).
- Not a bug; a feature to (re)introduce after the migration settles.
