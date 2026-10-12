---
title: Give the player their flag map even when they skip chicken onboarding
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:02:31 (`run/client2.log`):

> Already lost my flag just looking for coal. We should proabbly give the
> player their map somehow even if they don't use the chicken onboarding.

## Notes

Player played the no-chicken day-1 path; the flag's location became
unknowable once they wandered off (coal search). The map (or some
find-your-flag mechanic) should be granted without requiring the chicken
onboarding.

## Triage notes

Design item: a minimap / flag locator for when the player is lost. No mechanical fix; needs a product decision on scope (vanilla map vs custom).

## Decision (2026-10-13, walkthrough F1)

A **vanilla filled map, no custom decoration** (no X on the flag): a map is
only good if the player can tell which chunk the flag is in, and the
flag-centered map at a zoom where the flag's chunk is readable provides that.
No `questown:flag` map decoration, no registry/renderer work. Grant the map
**at flag placement** (the flag is already unwanderable-to right after day 1
begins; the map must be in the inventory from the start) — check what the
chicken path awards for the flag and align, so the chicken path doesn't
double-grant.
