---
title: 'Job-change quest card: BOP in the item row crowds out the job name — render the job name there instead (or drop the BOP)'
status: fixed
origin: 2026-10-15 playtest — user chat 03:30:46 + 03:30:54
---

# Job-change quest card: BOP icon vs job name

User rulings (two messages, second narrows the first):
1. 03:30:46 — "Since job change quests ALWAYS require a BOP. Maybe we can
   use the item rendering row to render the job name instead. Since it's
   usually so long it overflows the card."
2. 03:30:54 — "We can just not render the BOP."

Job-change quests always require a Block of Progress, so the BOP icon in
the card's item row is pure repetition; the job name is what's long and
overflows. Either (a) move the job name into the item row, or (b) drop the
BOP icon from that card type. Option (b) is the user's final word —
confirm which when picking it up (b, with the name-overflow addressed in
the title row per the "Post a work request" truncation work).

Option (b) implemented: both `QuestsScreen.renderRecipeCardIcons` and `QuestRemoveConfirmScreen.renderRecipeCardIcons` set the ingredients to an empty list for `Quest.QuestType.JOB_CHANGE` cards, so the grey BOP square no longer renders on those cards (the 32x32 job art and title remain). Completeness gating is unaffected - `quest.canComplete`/`complete` consult `recipe.getIngredients()` directly. See gui/COMPLEX.md "Playtest 2026-10-15". Committed 2026-10-16 on branch pt-ui.
