# Reuse Compat / Util / RenderUtil before calling Minecraft directly

**Rule:** before calling a raw Minecraft API for a common integration (text, tooltips,
components, sounds, item names, RNG, drawing), scan these three first and use the existing
wrapper if one fits:

- **`ca.bradj.questown.mc.Compat`** — the MC-integration seam. Wraps version-sensitive /
  frequently-used vanilla calls so they live in one place.
- **`ca.bradj.questown.mc.Util`** — general helpers (e.g. on-screen text).
- **`ca.bradj.questown.gui.RenderUtil`** — GUI rendering helpers (ellipsis/tooltip building,
  item strips).

**Why:** `Compat` exists to centralize Minecraft integration — reusing it keeps call sites
consistent and version-portable, and avoids re-implementing something that already exists.
Calling vanilla directly scatters the integration and misses these guarantees.

## Common wrappers (don't hand-roll these)

| Instead of vanilla… | Use |
| --- | --- |
| `font.split(text, w)` | `Compat.splitText(font, text, w)` |
| `Component.translatable(k, args)` | `Compat.translatable(k, args)` |
| `Component.translatable(k).withStyle(...)` / `setStyle` | `Compat.translatableStyled(k, style, args)` |
| `Component.literal(s)` | `Compat.literal(s)` |
| hand-drawn wrapped label text | `Compat.drawDarkTextWrap` / `drawDarkText` / `drawLightText` |
| `level.playSound(...)` | `Compat.playSound` / `playNeutralSound` |
| item display name from an `Item` | `Compat.getItemName(item)` |
| `player.displayClientMessage(...)` on-screen text | `Util.onScreenText(...)` |

Not exhaustive — read the three classes' public methods when in doubt. If no wrapper fits
and the call is a common integration, consider **adding one to `Compat`** rather than
calling vanilla inline, so the next screen reuses it.

There is no wrapper for *drawing* a tooltip (`Screen.renderTooltip`), so calling it directly
is fine — but build its lines with `Compat.splitText` / `Compat.translatableStyled`.
