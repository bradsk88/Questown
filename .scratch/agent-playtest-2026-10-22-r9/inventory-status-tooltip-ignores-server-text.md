---
title: "Villager inventory screen's status tooltip falls back to 'busy with their work' instead of the server's text"
status: needs-triage
created: 2026-10-23
---

## What was seen

Held `cook/fuel [no_coal]`, 2026-10-23, after the status need line landed (r9 #4). The cook was
`seeking_work`, status RELAXING, `need=UNMET_ITEM` coal (`_qtdev inspect villagers`). The flag's
villagers tab tooltip read "Cook is looking for supplies. They need Coal to work." (correct). The
villager's own inventory screen tooltip read "Cook is busy with their work." on three captures in
a row, which is `JobTooltips.FALLBACK_LINE_1`.

## Likely mechanism (BELIEVED, not instrumented)

`JobTooltips.get` looks up `ClientAccess.getStatusText(jobId, status)`, keyed by
`(packet.jobId, status)` (`ClientAccess.storeTextOverride`). `InventoryAndStatusMenu.statusChanged`
sends the packet with the menu's `jobId`, fixed when the screen opened, while the cook cycles
seeking_work / downtime / dining every few seconds. When the screen's key and the stored key differ,
the lookup misses and the client builds the standard keys, which fall back to "busy with their work".
Settle by logging both keys on the client when the tooltip renders.

## Why it matters

The status line is where r9 asked for the blocker to be named (principle 3). The flag villagers
tab gets it right; the villager's own screen shows a generic line for the same townie.
