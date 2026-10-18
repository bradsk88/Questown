---
title: '"New visitors will arrive after all players sleep" shows, but the flag does not promise a morning arrival'
status: resolved
origin: 2026-10-18 playtest (dev-server-12, build dd7a43f5) — user chat 21:33:29
---

"I got the "NEw visitors will arrive" nessage above. But the flag does not say "and one more in
the morning" I'm thinking that chat messsage should not have shown.."

## Facts (/tmp/dev-server-12.log)

- 21:30:15 town gate quest completed -> queued `dialog.visitors.instruction.sleep_visitors`
  ("New visitors will arrive after all players sleep", en_us.json:350).
- 21:30:39 the first villager (a65e35e4) registered — it arrived right away, not after a sleep.
- The flag status line `gui.status.villager_arriving_morning` ("...and one more in the morning")
  did not show (user observation), consistent with no further arrival being scheduled: at
  21:50:39 the next arrival was held ("raw-food (LOOT) quest not complete").

## Question

Should the sleep-visitors instruction only fire when a morning arrival is actually scheduled
(the same condition that shows "...and one more in the morning")? Find the broadcast's trigger
beside the town-gate completion before deciding.

## Resolved (2026-10-18)

The message is `MCQuestBatch.getCompletionMessage` for any batch whose reward holds a visitor. The one the user saw was the basics batch (21:32:26 `sleep_visitors` right after the job_board completion), whose arrival was held for raw food — so the flag rightly showed no "one more in the morning". The basics batch no longer brings a villager (two-villagers-after-hunter-gatherer.md); the remaining onboarding arrivals (gate, hunter-gatherer, cook) are never held at completion.
