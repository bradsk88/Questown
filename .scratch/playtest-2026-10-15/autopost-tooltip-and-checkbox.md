---
title: '"Auto-post new requests": tooltip not shown, checkbox does not toggle visually'
status: fixed
origin: 2026-10-15 playtest — user chat 03:04:17 + 03:04:38
---

# Job board auto-post toggle: no tooltip, no visual state

Two reports on the same widget (job board GUI, "Auto-post new requests"):

1. 03:04:17 — "needs an explanatory tooltip." The lang keys ALREADY exist:
   `job_board.auto_request.tooltip.on` / `.off` (en_us.json 462-463). So the
   gap is wiring/rendering in the job board screen — find the checkbox
   widget, check whether the tooltip is registered (hover region + tooltip
   list) and whether it is the on/off variant matching the current state.
2. 03:04:38 — "its checkbox doesn't toggle visually. It should." The glyph
   must change with state (checked/unchecked), and after a click the new
   state must re-render (check the click handler: does it request a
   re-render, or only send the request to the server?).

Root-caused: the round-trip already worked (server log 03:55: "Board auto-request set to false/true by Dev"; local flip is instant in `toggleAutoRequest`). The 03:04 reports were a hit-area problem: hover/click only registered inside a 16x17 box around the 9px glyph, so hovering the label showed no tooltip and the glyph was easy to miss. `WorkScreen` now measures the label (`font.width`) and registers the whole glyph+label span in a `wideRegions` map for both the tooltip and `mouseClicked`. See gui/COMPLEX.md "Playtest 2026-10-15". Committed 2026-10-16 on branch pt-ui.
