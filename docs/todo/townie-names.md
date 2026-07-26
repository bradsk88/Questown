Townie Names
=============

Decided (2026-07-26/27): **townies get names.** Custom naming should be available
to the player at a cost (possibly a **Block of Progress**). Names render the way
player and named-pet names do (world-space nameplate) — the **need bubble** stays
icon-only. What is *not* settled is where the default names come from; that is what
this doc is for.

Why it matters
--------------

Townies are anonymous today. `VisitorMobEntity` has no name handling, and
`message.villager.leveled_up` prints `UtilClean.truncateMiddle(uuid)`, so the
player reads *"a1b2…f9e8 reached level 3"*.

Under the watching-first stance (ADR-0011) this blocks more than polish. Every
per-townie system — **proficiency**, mood, need bubbles — produces individual
signal with no individual to attach it to. #269 exists so each townie has "a
distinct contribution", but the player cannot currently tell two townies apart, so
"Mira is your best baker" is a thought they cannot have.

Preferred direction: names derived from item names
---------------------------------------------------

The favoured idea is naming townies after things that exist in the world —
*Diamond*, *Stone*, *Shovel* — which gives Minecraft-native flavour for free and
scales with modded content. The problem is extracting name-like parts without
producing *"Slightly Tarnished Copper Block"*.

### Source the **registry path**, not the display name

Use `Compat.getItemId(item).getPath()`, not `Compat.getItemName(item)`:

- The path is already tokenized on `_`, which is exactly the split the algorithm
  needs. A display name would have to be re-split on spaces anyway.
- It is locale-independent. Resolving a translatable `Component` server-side pins
  to en_us from the jar regardless, and a *stored* name should not shift when the
  player changes language.
- Modded display names can carry styling/formatting codes; paths cannot.

### Stoplist-collapse

Tokenize the path and **drop** tokens on a stoplist, then take what survives:

1. **Material states / finishes** — waxed, exposed, weathered, oxidized, cut,
   polished, chiseled, smooth, cracked, mossy, raw, cooked, damaged
2. **Forms / generics** — block, item, slab, stairs, wall, fence, gate, door,
   trapdoor, button, plate, pane, bars, brick(s), tile(s), ingot, nugget, dust,
   shard, powder, ore, log, planks, sapling, seeds, bottle, bucket
3. **Qualifiers** — lesser, greater, small, large, tiny, huge, upper, lower,
   dead, infested

Then: reject tokens containing digits, keep length 3–10, title-case.

The pleasing property is that noisy names collapse to their good core rather than
needing to be individually excluded:

| Path | Result |
|---|---|
| `diamond` | Diamond |
| `waxed_oxidized_cut_copper_stairs` | **Copper** |
| `oak_log` | Oak |
| `stone_bricks` | Stone |
| `iron_shovel` | Iron *(or Shovel — tools stay off the stoplist deliberately)* |
| `golden_apple` | Golden *(or Apple)* |

### The filters that do the real work

The stoplist alone is not enough; three cheap filters remove most of the garbage:

- **Namespace** — `minecraft:` only by default, config to widen. Modded registries
  are the main source of unusable and occasionally crude strings.
- **Creative-tab membership** — excludes technical items (`command_block`,
  `structure_void`, `debug_stick`) without naming them individually.
- **A short curated blocklist** — for results that survive everything and still
  read badly: *Rotten* (`rotten_flesh`), *Poisonous* (`poisonous_potato`),
  *Suspicious*, and similar. This part cannot be purely algorithmic and that is
  fine; the list stays short because the stoplist does the heavy lifting.

### Decided: generate algorithmically, check the `minecraft:` pool in

**Generate the pool algorithmically, then check the generated pool in as a
reviewable list** (golden file, asserted by JUnit), scoped to the `minecraft:`
namespace. This dissolves the "algorithm vs curated list" tension rather than
picking a side.

- The full name pool is visible and tunable in one place instead of being
  discovered one bad name at a time during play.
- The algorithm keeps the item-flavour and the modded scaling.
- Regenerating after a stoplist change produces a reviewable diff, so tuning is
  cheap and regressions are caught by the test rather than by a player meeting a
  townie called *Tarnished*.
- Community name donations (the RimWorld model) can be appended to the same list
  later without any code change.

Plan for mod-sourced names (future)
------------------------------------

Only the `minecraft:` pool ships checked-in, but mod-sourced names are an intended
future direction, and two choices made now decide whether that stays cheap:

**Write the extractor as runtime code, not a build-time script.** Use it to
*generate* the checked-in pool, but keep it callable at runtime so the future
mod-sourced path is the same function pointed at a different namespace set —
one code path, two entry points. If the extractor only ever exists as an offline
script, adding mod names later means rewriting it.

**Pool = data, not a hardcoded list.** Load the checked-in pool as a resource so a
future config flag can *append* names extracted at runtime from non-`minecraft:`
namespaces (behind the same stoplist and filters). Datapack-loadable would also let
players supply their own list, which is the natural home for community name
donations.

**Persist the resolved string on the townie, not the item id.** A townie named
*Copper* keeps that name if the mod that produced it is later removed. Storing an
item reference would orphan names on mod removal — a nasty, save-breaking class of
bug for a purely cosmetic feature.

Open
----

- Which token to pick when several survive (`iron_shovel` → Iron or Shovel):
  first, last, or deterministic-by-uuid so it varies across townies.
- Uniqueness within a town, and behaviour when the pool is exhausted.
- Whether custom naming costs a Block of Progress, and where that UI lives.
