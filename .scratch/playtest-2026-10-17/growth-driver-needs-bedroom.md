---
title: 'village/growth driver stalls at roster 2: it cannot complete the bedroom quest'
status: ready-for-agent
origin: 2026-10-17 verification runs (growth-2 + HEAD baseline)
---

The hunter-gatherer batch (raw food + 4 stone swords + hunter job change + bedroom)
pays out the third villager. The growth driver now does the hunter change
(`spendBopOnOpenJobChange`) and deposits the swords, but never places a bed in a
registered room, so the batch never completes and the run sits at roster 2 until
`MAX_TICKS`. It also never posts a work request (the first-work-request quest).

HEAD baseline (0e6a9014, before the arrival-gate rework) stalled at roster 2 too,
with raw food / hunter / bedroom open — so this is a pre-existing driver gap, not a
regression.

Next: add a bed to one of `buildVillage()`'s rooms (player item-use path, like
`placeJobBoard`), and post one request via the owner's request path, then re-run to
roster ≥ 5.

## Update (2026-10-19, after the onboarding-arrival rework 7c7d4fe9 + night sleep)

The driver now gets past the bedroom: the hunter-gatherer batch completes (villager 2 arrives),
and the cook batch's Small Storeroom -> Storeroom and Bedroom -> Big Bedroom upgrades both
complete. The run now stalls at roster 2 on the cook batch's last open quest, 8 cooked food
(`villager_food` LOOT): villager 2 is staffed as cook but sits in `cook/seeking_work` for the rest
of the run (`build/autotest-logs/growth-0119.log`). Matches AUTOTESTS.md's "never a cook" for
this scenario — next driver gap: a kitchen the cook can work (furnace + raw meat + fuel supplied).
The executor now sleeps through nights (20 nights slept, 240 000 ticks in 1758 s).
