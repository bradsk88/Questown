Procedural Quest Room Recipes in Flavor Text
==============================================

The first 3 procedural batches now include generic flavor text:
"Your village needs a [Room Name]. Build one to keep production flowing."

This doesn't tell the player WHICH BLOCKS to place in the room. For
unfamiliar room types (smelter, clinic, etc.), the player must consult
the journal's Room Recipes lesson page or experiment.

Future improvement: include the key block in the flavor text:
  "Build a Smelter Room — place a blast furnace in an enclosed room
   and register the door."

This requires resolving the room recipe's primary ingredient to a
human-readable block name at quest generation time. The room recipe
system already has ingredient lists — the first/primary ingredient
could be extracted and named.

Alternatively, the v4 "Room Recipes" lesson page (gated on
first_job_done) could be made more discoverable — e.g. a chat hint
linking to the journal page when an unfamiliar room quest is added.

Source: docs/onboarding-player-experience.md, Critique #9
