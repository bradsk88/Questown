# GUI layout — flow layout + the `gui-lint` oracle

**Why this exists:** dynamic text (lang strings, and especially *translations*) overflows
fixed-position layouts. This repo has hit it more than once — see
`docs/solutions/bugs/resolved/quest-flavor-test-cutoff.md` (quest flavor text overran its
card into the icons) and the 2026-07-14 flag-crafting overlap (recipe descriptions bled
into each other and the "Begin moving" button). Treat GUI text layout as a correctness
concern, not a cosmetic one.

## Rules for building a screen

1. **Flow layout — never hardcode Y positions for text blocks.** Compute each block's
   position from the *actual* rendered height of the block above it:
   `font.split(component, width).size() * lineHeight + padding`. Advance a running `y`
   cursor and size the panel to the total. Then a longer string (or a translation) just
   pushes everything down — it can't overlap. `gui/FlagCraftingScreen.java` is the
   reference example (`computeLayout()` shared by `init()` for widgets and `render()` for
   text).
2. **Give wrapped text its full width.** Don't pass a cramped wrap width — that was the
   root cause of the flag-crafting bug (56px instead of the full row), which forced 4+
   lines and vertical overflow.
3. **Prefer tooltips for secondary/description text** on interactive elements to keep
   panels compact (e.g. a recipe's "turn X into Y" belongs on the button's tooltip, not
   as a text block). Use the `Button(x,y,w,h,msg,onPress,onTooltip)` constructor.

## The oracle — verify, don't eyeball

`gui/GuiLayoutCheckClientEvents` runs `gui/GuiLayoutLinter` against every `ca.bradj.questown.gui.*`
screen as it opens (dev-only; inert in shipped jars via `FMLEnvironment.production`). It
captures each wrapped-text bounding box at the `Compat.drawDarkTextWrap` choke point and
feeds them, plus the widgets, into one overlap/bounds check.

It catches: **off-screen** widgets, **label-overflow** (button text wider than the button),
and **overlap** (widgets or wrapped text colliding — including text bleeding into the next
element).

**After any GUI change, open that screen in a dev client and confirm the log line:**

```
[gui-lint] <ScreenName> (WxH) — OK, no layout violations
```

A `WARN` with `... N layout violation(s)` names each collision by element. This is the
objective pass/fail — do not rely on a screenshot looking fine at one window size.

**The loop that works:** playtest finds it → `gui-lint` locates it → flow-layout fix →
`gui-lint` confirms green.

Pure-logic invariants are unit-tested in `test/gui/GuiLayoutLinterTest.java` (no client
needed). The end-to-end capture only runs in a client render, so its confirmation is the
in-game `[gui-lint]` line above.
