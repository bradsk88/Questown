# Decision: Silent World Access for Simulation

**Date:** 2026-02-12
**Status:** Active
**Revisit when:** A presentation side effect causes visible
bugs during warp, or when adding particles/chat/animations
to QTWorldAccess

---

## Context

Questown's time warp simulates hundreds or thousands of ticks
of villager work in a compressed loop. Job rules (tilling,
planting, harvesting) need to mutate real block state — drops
must be calculated, crop ages must advance, tool transforms
must apply. But perceptual side effects (sound, particles,
animations) aimed at human observation would be jarring or
wasteful when compressed into a single frame.

We needed a way to let rules run identical code in both
realtime and warp without duplicating rule implementations.

---

## Decision

`MinecraftWorldAccess.silent(level)` returns an instance where
presentation methods (`playSound`) are no-ops while all
state-mutating methods delegate to the real `ServerLevel`.
Rules don't know or care whether they're running in silent
mode.

---

## Why

1. **One rule implementation for both paths.** Rules call
   `world.playSound()` freely — it just does nothing during
   warp. No if/else, no separate warp rule classes.

2. **Simple and obvious.** A boolean flag on the implementation
   is easy to understand, easy to audit, easy to extend.

3. **Callers decide** The warp loop creates a silent instance; 
   the realtime loop creates a normal one. Rules are unaware.

---

## Risks

**Particle leaks.** Some methods like `useItemOnBlock` call
vanilla MC code (`Item.useOn()`) which may fire particle
packets internally. The `silent` flag only gates methods we
control (`playSound`), not side effects buried inside vanilla
calls. This could send spurious particle packets to nearby
players during warp.

**Channel sprawl.** As `QTWorldAccess` gains more presentation
methods (particles, chat, animations), each needs its own
`if (silent) return;` guard. Forgetting one may leak effects.

**Sound-as-physics.** Vanilla's sculk sensor reacts to sounds.
If a rule plays a sound that a sculk sensor should detect,
silent mode suppresses it. This is acceptable — warp already
doesn't simulate redstone.

---

## Why the Risks are Acceptable Today

- Particle leaks during warp are cosmetic — no player is
  typically near the town during warp (they just arrived and
  warp happens before they see anything).
- The interface has only two presentation methods (`playSound`
  x2). Channel sprawl is a future problem.
- No Questown rules currently depend on sound-as-physics.
- Ignored sounds (e.g. the sculk sensor mentioned above) 
  **already** have no effect when a player is absent from the 
  chunk. Since time warp is intended to simulate behaviour 
  that happened while the player was away from town, the QT
  behaviour is consistent with how Minecraft works natively.

---

## Guidance for Modders

Modders writing custom `JobPhaseModifier` rules that interact
with the world via `QTWorldAccess` should be aware:

- **Rules run during both realtime and warp.** The same code
  executes in both contexts.
- **`playSound` is automatically suppressed during warp.** No
  action needed.
- **Avoid firing effects directly through `asServerLevel()`.**
  If a rule uses the escape hatch to access `ServerLevel`
  directly and spawns particles, plays sounds, or sends
  packets, those WILL fire during warp. Prefer using
  `QTWorldAccess` methods which handle suppression.
- **If your rule triggers vanilla code with side effects**
  (e.g., `Item.useOn()` which may spawn particles), accept
  that minor cosmetic leaks are tolerable during warp. If
  they become problematic, contribute a new `QTWorldAccess`
  method that separates the state mutation from the effect.

---

## Migration Path (When Needed)

If the number of presentation channels grows, consider
replacing the boolean flag with a strategy:

```java
interface PresentationPolicy {
    void playSound(BlockPos pos, SoundEvent sound);
    void spawnParticles(BlockPos pos, ParticleOptions options);
    // ... future channels
}
```

`MinecraftWorldAccess` would hold a `PresentationPolicy` —
either a real one or a no-op one. This keeps the gate logic
in one place instead of scattered across methods.
