~~If the /qt test command finds an existing flag. It should use THAT as the origin position for the test, rather than
using the player's position. It should still destroy the old flag and do everything else that it currently does. Just
the origin position should be different.~~

**DONE** — TestExecutor.destroyNearbyFlags() now reuses existing flag position as origin.
Also: commands moved from `/qt test` and `/qt testall` to `/_qtdev test` and `/_qtdev testall`.