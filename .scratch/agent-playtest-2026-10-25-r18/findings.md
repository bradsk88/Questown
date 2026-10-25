# Agent playtest 2026-10-25 r18 (fresh world, worktree qt-wt-work at c2a0307c)

Goal: a fresh-world onboarding with the r17 fixes in, and the live checks they still need:
the wall-built furnace (#19), the deposit exit (#17), the BOP tab (#3/#11), crafting rows (#1),
skills frames (#22), the BOP message on two lines (#18), the cooked-food quest, the leave puff (#4).

## Interventions
- 01:36: cleared and levelled x 70..100, z 150..180 (air y64-90, grass y63, dirt y60-62) by console:
  the spawn was dense forest canopy (a creative player's flattening); Dev tp to (85, 64, 165).

- Day 0, ~01:40: `time add` from daytime 7596 to 12700 (dusk) to sleep night 0 without waiting out the day.
- Day 1: 4 stone swords into the storeroom chest slots 0-3 by console (the player's delivery);
  bedroom built by console fill (door (82, 64, 159), bed, torch); BOP spent on Villager 1 via the
  change-job tab -> Hunter. `time add` from 9090 to dusk to sleep night 1.
- Day 2: room 3 (door (88, 64, 159)) built and wanded; work request posted (Raw Porkchop) via
  `inquire`; kitchen (door (94, 64, 159)) built with the furnace **in its east wall** at (96, 64, 157)
  (the r17 #19 shape); second storeroom chest by `clickheld` (77, 64, 156); second bed by console.
  Agent error: an `item replace ... from entity` hotbar shuffle *copied* a BOP (it does not move);
  the copy was cleared and the cook change used a BOP taken from the flag.
- Day 2: BOP spent on Villager 2 via the change-job tab -> Cook. 4 coal into storeroom slot 20 and
  4 raw mutton into slot 21 by console (the player's deliveries; the cook only knows mutton).

## Findings
1. (open) r17 #17's new `bop-deposited` exit was not exercised live: both deposits this run left
   via `ready-for-downtime` (the hunter's) or after the overnight pause (the cook's). Still covered
   by `BoardIdleExitsTest` only.
2. (judge, known) The onboarding cook only knows mutton while the hunter brings porkchop/chicken;
   hungry villagers eat the raw meat (`dining_raw_food`), so the Cooked Food quest moves only with
   mutton (1/8 after 4 delivered mutton). Same as the parked r16 design question.
3. (agent tooling) `vopen` needs the flag villagers tab open first (`flagtab villagers`); its
   refusal goes to the client's stdout (`build/agent-playtest/<name>-client.log`), not the client
   log. `item replace ... from entity` *copies*: it duplicated a BOP here.
4. (minor) A welcome mat clicked onto a cell the seeded starting timber occupies fails silently
   (`-> FAIL`, no chat), which a player would read as "the mat doesn't work". Vanilla placement
   behaviour, noted only.

## Fine (checked)
- r17 #1: day-0 crafting tab shows 3 rows (wand, mat, board); mineshaft and fishing station hidden.
- Day-0 onboarding (flag, campfire, gate, room, board, storeroom) in ~5 real minutes.
- r17 #8: on wake, chat "You feel groggy from sleeping on the ground: Mining Fatigue and Weakness
  for 5 minutes." r17 #13: campfire still lit at dawn (`campfire[lit=true]`).
- Dawn 1: Villager 1 (1fcfe9b3) arrived; first-work-request batch posted.
- r17 #3/#11: BOP tab Take button bottom-aligned; after a take the tab stayed open showing 2 (was 3).
- r17 #7: quests panel holds the "View completed quests" button.
- r17 #20: villager tabs have no Quests tab.
- Hunter's first trip: raw porkchop closed Raw Food (`extracted 1/1`); no sword-fetch stall.
- r17 #19 on a fresh world: the cook targeted the furnace built into the kitchen's east wall
  (`jobBlock=(96, 64, 157), entityFeetPos=(95, 64, 157)`, inside) and fuelled it.
- Cooked Food (0/8) card: raw porkchop / chicken / a feather extracted by the hunter do not count.
- r17 #18: the BOP deposit chat shows two lines ("...deposited at the town flag!" / "Right-click the
  flag to collect it."; screenshot r18-p). The client log escapes it as a literal `\n` (vanilla logger).
- Cook XP: `+200 from cook:fuel` / `cook:mutton`; the cook reached level 2.
- Cook BOP deposit paused overnight (cook in bed, deposit job hit its 3000-tick limit and re-entered)
  and went through after dawn: night sleeping, not a stall.
- r17 #22: skills tab frames full and centred behind the job icons (zoom r18-q-zoom).
- Cooked Food card read 1/8 after the cook's cooked mutton; raw meat and a feather did not count.

## Session end (02:05)
Stopped by pid/FIFO; world + logs archived to `~/questown-worlds/r18-agent-playtest-2026-10-25.tgz`.
Reached day 3 with 2 villagers (hunter level 3, cook level 2), every day-0..2 onboarding quest done
except Cooked Food (1/8).
