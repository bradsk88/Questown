---
status: ready-for-agent
origin: 2026-10-12 playtest — user chat 17:32:07
---

# Flag crafting menu: no tooltips; border differs from the quest UI

Two observations on the flag's crafting tab (`FlagCraftingScreen`):

1. **No tooltips.** The input items, the output item, and the Craft button
   should all have hover tooltips (what the input is, what you get, what the
   button does / its cost). The vanilla crafting grid gets this for free from
   item tooltips; our custom screen draws its own slots.
2. **Border mismatch.** The crafting menu's border looks faded/different from
   the rest of the flag UI (quests tab). All flag screens should share the
   same border treatment.

Check `FlagCraftingScreen` vs `QuestsScreen` (and the shared frame helper in
`RenderUtil`/`Tabs`) for the border draw; tooltips follow the existing
`renderCraftTooltip` pattern already present for the button.
