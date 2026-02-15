Villagers do not work at night. But the warp, regardelss of when it starts, "pretends" that it is daytime.

The purpose of warp is to _simulate_ real productivity while the player is away from town, rather than ticking it.

This means that a player who leaves at night and returns in the morning will (assuming I'm correct about this bug) see
a bunch of productivity results even though the villagers should have been relaxing/sleeping.

Possible Solution:
- When a warp of (e.g.) 20000 ticks is requested, first calculate the amount of "night time" that would pass between 
  "now" and the next morning and subtract that from the number of requested ticks. Then run a warp on the result of the
  subtraction.