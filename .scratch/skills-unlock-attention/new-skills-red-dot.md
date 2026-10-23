---
title: 'Skills page: show when there are new skills to unlock'
status: needs-triage
---

# Skills page: show when there are new skills to unlock

Source: agent playtest 2026-10-23 r10 finding #14
(`.scratch/agent-playtest-2026-10-23/findings.md`), dev ruling 2026-10-23.

The onboarding cook only knows cook/mutton (`starter_recipes` in
`questown_jobs/cook_simple_furnace_food.json`); beef/porkchop/chicken are BOP unlocks. With the
hunter bringing mostly beef/porkchop/chicken, the cook idles and nothing tells a first-time
player that spending a Block of Progress on the cook's skills is the way forward.

Dev direction: a holistic feature, not a status-copy patch:

- a red dot (attention marker) on the villager's skills tab/page when there are new skills to
  unlock (the BOP tab and change-job tab already have the same kind of marker:
  `VillagerBopTab.attention`, `VillagerTabs.CHANGE_ROOT_ATTENTION`);
- investment in the skills UI so it is clearer how players are meant to interact with it.

Open: what "new skills to unlock" means (any affordable unlock? one newly reachable by level?),
and whether the dot shows on the flag's villagers page too. Watch tab capacity (dev, 2026-10-23:
the villager tab strip may be at capacity).
