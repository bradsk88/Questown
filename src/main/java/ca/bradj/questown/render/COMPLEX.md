# render/ — BubbleRenderer

## The "!" attention bubble anchors one block up, not two (2026-10-14)

`BubbleRenderer.renderTextureBubbleAtBlock` (the "!" bubble for a flag with incomplete
quests / a job board with no work requests, driven from `NeedBubbleClientEvents.onRenderLevel`)
shares its history with the dead-door bubble: the anchor was written for a two-block door
(`pos.getY() + 2.0 + HEAD_LIFT_BLOCKS`), and the "!" targets inherited it. Flags and job
boards are one-block-tall, so the bubble floated a full block too high (user report,
playtest 2026-10-14). The texture path now anchors at `pos.getY() + 1.0 + HEAD_LIFT_BLOCKS`;
the item path (`renderIconBubbleAtBlock`, dead doors only) keeps the 2.0 anchor — the two
paths serve different target heights and must not be re-unified without checking both.

## Entity bubbles clear the nameplate (2026-10-22)

Both entity paths (`renderBubble`, `renderTextureBubble`) lift through `bubbleLift(entity)`:
`getBbHeight() + HEAD_LIFT_BLOCKS`, plus `NAMEPLATE_CLEARANCE_BLOCKS` (0.3, about one
nameplate line) when `entity.shouldShowName()`. Townies have carried a visible "Villager N"
name tag since 2026-10-14, and vanilla draws it at the same height the bubble used, so the
bubble covered its end ("Villag[!]2"). Dev ruling: raise the bubble. Verified live (ticks
frozen, 3 blocks from a needy townie): the bubble sits above "Villager 14" with its tail on the
name. Block anchors (flag, job board) are unchanged.

## The bubble icon turns 180° about Y (2026-10-31)

After `cameraOrientation()` local +Z points away from the camera, while the GUI item transform
(`Compat.renderItemStaticGui`) expects the viewer at +Z, so the icon was drawn from behind: a
block showed its underside (dev playtest 2026-10-30, the Smithy bubble's blacksmith table) and a
flat item was mirrored and dark (lit from the back). `renderBubbleAssembly` now turns the icon
180° about Y inside its own push, after the background. Verified in a held
`cook/fuel [open_request_no_pork]` run against the hotbar's raw porkchop: unflipped the chop ran
upper-left to lower-right and darker, flipped it matches the hotbar. (The old comment said the
HEAD transform already turned it; on this path it doesn't.)
Open: water behind a bubble draws over its background (seen against the ocean), flip or not;
ticket `.scratch/dev-playtest-2026-10-30/bubble-background-under-water.md`.

