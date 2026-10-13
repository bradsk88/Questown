---
title: 'Villager UI: with the economics tab open, the "switch job" tab also appears selected'
status: fixed
---

# Economics tab: "switch job" tab also appears selected

Captured 2026-10-14 (server-19, in-game chat 04:17:41):

> "When viewing the economics tab of the villager UI, the 'swich job' tab
> also appears selected"

Note: reported on server-19, which already carried the 2026-10-14
selected-tab fix (`d3156224`: `Tab.selected = fn == null` is the
current-pane marker; the change_root tab's flag now uses its own fn).
So this is either a variant that fix missed (the switch-job tab's fn is
null on a path where it shouldn't be, or two tabs get null fns at once) or
a different selection signal (highlight vs lit). Needs a live look at the
tab construction for the villager menu (gui/VillagerMenus / VillagerTabs)
while the economics pane is open.

Related day-1 item: `flag-playtest`/Issue #4 in this session's
playtest-report.html (selected-pane marker).

## Root cause + fix (2026-10-14 night)

A tab renders "selected" when its open-fn is null (`Tab.selected =
fn == null`, the d3156224 marker), and a pane's open-fn is null exactly when
the open pane's `getEnabledTabs()` omits that tab's type. Every villager
pane builds its list as `VillagerTabs.except(ownTab)` EXCEPT
`VillagerEconomicsMenu`, which used a hand-rolled `ImmutableList.of(...)` that
omitted `CHANGE_ROOT` — so with the economics pane open, the "switch job"
tab's fn was null and it rendered selected alongside the economics tab.

Fix: `VillagerEconomicsMenu.ENABLED_TABS = VillagerTabs.except(ECONOMICS)`
(same construction as every sibling pane). Pinned by
`VillagerEconomicsMenuEnabledTabsTest` (the list is a private static on a
registry-bound menu, so the pin reads it reflectively — the initializer is
pure string math). Red-verified by stash: pre-fix code fails the
change_root + sibling-invariant cases.

FIXED <pending-commit>
