Flag Speech Bubble (Quest Hint Indicator)
==========================================

Render a floating "speech bubble" above the town flag showing the most
relevant item icon for the current quest. For example, early in the
tutorial it might show a town wand icon, drawing the player's attention
to the flag and hinting at what to do next.

Advantages over a boss bar:
  - Diegetic (part of the world, not HUD overlay)
  - Only visible when the player is near the flag
  - Fits the mod's narrative framing (the flag is the town's center)
  - Naturally fades with distance, avoiding HUD clutter during exploration

Alternative: Boss bar with permanent disable toggle in flag UI. Higher
effort, more intrusive, but visible from anywhere.

Implementation requires:
  - Determine "most relevant quest" logic (first incomplete quest in
    current batch? highest priority?)
  - Map quest types to representative item icons
  - Render floating item entity or custom particle above flag block
  - Consider rendering only during tutorial phases or always

Source: docs/onboarding-player-experience.md, Critique #8
