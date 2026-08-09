# Zombie door registrations are discarded when the door block is gone

status: accepted
date: 2026-08-09

ADR-0011 rejected deregistering *dead* doors (a registered door whose room scan
fails — the half-built-room case) because silently undoing the player's action
is worse than silence. It deliberately left open the **zombie** case: the
registration remains but the door *block is gone from the world* (the player
tore the shed down). This ADR closes that: zombie registrations are simply
discarded when the room scan observes the block is missing.

The two cases split on whether the registration still has a referent. A
standing door with no room is a normal mid-build state — it gets a **need
bubble** (diagnosis), never deregistration. A gone door is stale data with
nothing to point a bubble at — it gets cleanup, silently.

**Silence is deliberate, not an oversight.** No chat broadcast accompanies the
discard: a room that was never completed had no function to lose, so the
deregistration costs the player nothing. (A door whose room *did* exist already
announces its own loss via the existing room-destroyed broadcast.)

## Consequences

- **ADR-0009 (relocation) no longer needs to drag zombie doors along** — the
  accumulation problem noted in ADR-0011's Consequences shrinks to at most one
  scan interval's worth.
- A player who breaks a door block intending to replace it (e.g. a fancier
  door) loses that registration and must re-wand. Accepted: the wand is cheap,
  and no functioning room existed to be harmed.
- The discard happens on the next real room scan in loaded chunks. If a player
  tears down a door and immediately leaves (warp, distance), the zombie rides
  along until the town is next scanned — invisible to everyone in the meantime,
  hence harmless.
