---
title: 'Item/LOOT quest progress indicator missing a space before "(0/4)"'
status: ready-for-agent
origin: 2026-10-15 playtest — user chat 03:07:06
---

# Card title renders "Carrots(0/4)" / "Raw Food(0/4)"

User report: "The item quest needs a space before its '(0/4)' indicator."

`UIQuest.getName` appended the progress suffix with no leading space:
`name.append(Compat.literal("(" + ...))` in both the ITEM and LOOT cases.
Fix (applied 2026-10-15, same session): the literal now starts with a space
— " (n/m)" — in both cases. Client-side render code; next client restart.
