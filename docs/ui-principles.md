# What a Questown UI feels like

Standing rules for every Questown screen, tooltip, chat line and in-world bubble. They come from
long-established interface practice (Nielsen's ten usability heuristics, 1994; Norman's *Design of
Everyday Things*; the Gestalt grouping laws; Fitts's and Hick's laws; the diegetic/non-diegetic
split from game-UI research, Fagerholt & Lorentzon 2009) and from Minecraft's own interface
language, which a Questown player has already learned. Each rule says what to do, why, and how to
check it. The layout mechanics (flow layout, the `gui-lint` oracle) are in
`docs/solutions/conventions/gui-layout-flow-and-linter.md`; the copy rules that are domain
decisions are in `docs/design-principles.md`.

## 1. It looks like Minecraft

Questown is a mod: the player arrives fluent in vanilla's panels, slots and tooltips. Borrow that
fluency instead of inventing a new look (consistency and standards; recognition over recall).

- The grey bevelled panel, 18 px item slots and the vanilla font, at the GUI scale the player
  chose. No gradients, rounded corners, drop shadows on panels, or emoji.
- Every flag screen uses the same frame (`JEI.getRecipeGuiBackground()`). A screen with a
  different border reads as a different mod.
- Pixel art at its true size: blit 16×16 and 32×32 art with its own sheet size (`Util.blit`'s
  10-arg form), never stretched (AGENTS.md "1.20.6 blit UV").
- Item tooltips follow vanilla: the item's name first (white), secondary lines grey, no brackets
  around names (`Compat.getHoverName`, not the chat form).

- Controls look like vanilla controls: a toggle is vanilla's checkbox sprite
  (`RenderContext.checkbox`), not a grey square with a white fill (the job board's auto-request
  toggle read as a blank square until 2026-10-22).

**Check:** put the screen next to a vanilla chest and crafting table at GUI scale 2 and 3. Does
anything look foreign?

## 2. The town is the interface; menus are the ledger

Questown's world is a town that works. Show the town's state where it happens first (a villager's
need bubble, the "!" over the flag or the job board, a chest that fills), and keep menus for the
details (diegetic before non-diegetic). A bubble names one thing, the most urgent, and the menu
explains it.

- One bubble per target, chosen by the crosshair contest (`AttentionBubbles`, `NeedBubbleClientEvents`).
- A menu never holds information the world could show at a glance, and the world never shows
  information that needs a paragraph.

**Check:** can a player who never opens a menu tell which townie is stuck and on what?

## 3. Always say what is happening and why (visibility of system status)

A townie standing still must say why. A stall the player can't read looks like a bug, even when
it is a design state ("unlock ≠ workability", `docs/design-principles.md`).

- Status text names the blocker the player can act on ("needs coal"), not the internal state
  ("NO_JOBSITE"), and never a raw id or lang key (`cook:simple_furnace_food`,
  `tooltips.villagers.job...`). A missing lang key falls back to readable text
  (`Compat.translatableWithFallback`), never to the key.
- Feedback for every action, at once: a click that does something shows it immediately (a claimed
  card disappears on the click, not on the next refresh); a click that can't do anything says
  why instead of doing nothing.
- Progress is a count the player can check: " (3/8)" against what they can see in the chest.

**Check:** for every status a townie can show, could the player act on it without the wiki?

## 4. Tell the player the next action, not the system

Copy is short, plain and concrete (match between system and the real world).

- Sentence case. Verbs on buttons ("Craft", "Request"). Lead with what to do: "Use wand on
  campfire", not "The campfire can be used by right-clicking with your wand at night".
- Name things by what the player sees: "Bedroom", "Raw Food", "Villager 2", never ids, UUIDs or
  jargon ("job root", "sub-job", "LOOT quest").
- No em dashes, no doubled words ("for villager Villager 1"), no explaining the design. Tooltips
  are about half the length you first wrote (dev, 2026-10-13).
- Tease, don't call to action, when there is nothing the player can do yet (P10): "Come back once
  you have at least one villager", not "Press + to request".
- Never claim what isn't true ("no one can make it" while a producer exists). If the claim depends
  on state, compute it or drop it. The change-job panel says "random profession" even while an
  onboarding quest guarantees the root (2026-10-22): say what will actually happen.

**Check:** read every new string aloud. Would a player say it that way? Does it name anything they
can't see?

## 5. Group by meaning, align to a grid (Gestalt)

- Related things sit together and unrelated things sit apart (proximity): a card's icon, name and
  progress form one unit; a toggle belongs to the panel it configures, inside its border.
- Align left edges and baselines on a shared column. Right-align numbers and levels ("Lvl 2")
  on the line they qualify.
- Leave a line of space between a headline and its body. A body glued to its headline reads as
  one run-on line.
- Order rows stably. A list that re-sorts while the player reads it (economics rows) breaks the
  player's place; sort by a stable key, and change order only when the player acts.

**Check:** `gui-lint` OK, then squint at a screenshot. Do the groups you meant show as groups?

## 6. Fit the space; never overflow (aesthetic and minimal)

- Text wraps to the space it has, and the space grows to fit (flow layout). Nothing draws past
  its card, panel or the screen edge at any GUI scale or translation length.
- A label that can't fit is shortened with an ellipsis and keeps its full text in a tooltip;
  primary information is never tooltip-only.
- Every element earns its place: if removing it wouldn't change the player's next action, remove
  it (`docs/design-principles.md`, "trim onboarding aids").

**Check:** `[gui-lint] <Screen> — OK, no layout violations` at GUI scale 2 and 4.

## 7. Attention is a budget

Red dots, "!" bubbles, toasts and chat lines all compete for the same attention, so each one
must mean "there is something new for you here" and must clear when the player has seen it.

- A dot clears when the player opens the tab it marks, and doesn't come back for the same thing.
- A toast belongs to a moment ("Your camp is ready" at dusk), not a whole day.
- One chat line per event. Same-tick duplicates are a bug (`BroadcastPacer` drops them), and two
  quest announcements in one slot are one too many.
- The screen a player opens lands on the tab that is asking for them (a dotted tab first).

**Check:** play a day and count the interruptions. Did each one lead somewhere?

## 8. Big, stable targets (Fitts)

- Clickable things are at least a slot (16-18 px) and look clickable: a card highlights on hover;
  a whole card is the target, not a few pixels of icon.
- The same action sits in the same place on every screen (page arrows, close, the "+").
- Paged lists page with the scroll wheel as well as the arrows, like the 1.19.2 mod did.

**Check:** can you do the screen's main action with one quick, imprecise click?

## 9. Few choices at a time (Hick); reveal the rest later

- Show what the town can do now. Hide tabs, jobs and options until they mean something (the
  skills tab appears at level 2, when the townie first has a job to unlock).
- Lists show only what applies: the jobs that can make this item, not every job in the data.
- Progressive disclosure: the card says what, the tooltip says how, the journal says why.

**Check:** on day 1, does every visible control have a use on day 1?

## 10. Prevent the error, then make it reversible

- Disable what can't be done and say why on hover, rather than letting a click fail silently.
- A double-click must not double-act (disable the button before sending).
- Destructive actions are rare, confirmed, and undoable where possible (a removed request can be
  re-posted; a broken block drops itself with its NBT).

**Check:** mash the main button twice. Did anything happen twice?

---

**Where these came from:** dev feedback from the 2026-10-11 .. 10-22 playtests (`.scratch/`), each
rule's examples are real tickets, and the general practice above. When a new screen or string
breaks one of these, fix the screen; when a rule turns out wrong for Questown, change this file
in the same commit as the screen that taught you.
