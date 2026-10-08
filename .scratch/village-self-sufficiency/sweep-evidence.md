# Self-sufficiency sweep evidence (verification that tick-range fix holds + next stall)

Durable record (world logs live in ephemeral /tmp — snapshot here as runs progress).

## Sweep A (830–848, partial — /tmp wiped at 19:26, evidence below is from live reads)

| world | spawn(x,z) | result at last read |
|---|---|---|
| 830 | (0,·,0) unrec | PASS-early: TOOKs (hunter/sword, cook/mutton, cook/fuel), cook_unlocked, LOOTTRACE 4+ |
| 831 | **(−112,160)** hostile | day=3, LOOTTRACE 65+, cook unlocked; late: ghost `ROOM/ACTIVE kitchen_stocked` ×3 despite `ROOM/COMPLETED kitchen_stocked`; leaver flipping `Job changed to cook/extract`; `PROBE-C isJobBlock FALSE cook/extract + gather/gatherer` flood |
| 832 | (0,·) | **FULL COMPLETION** (executor DONE, clean save) day=2 |
| 833 | — | day=3, LOOTTRACE 60+; `DEV-DIAG:kitchen recipe=kitchen_small matches=2` |
| 834 | — | day=3, LOOTTRACE 63+; `Job changed to cook/extract` |
| 835 | — | day=3, LOOTTRACE 60+; jcscan sees 18 quests progressing |
| 836 | — | day=3, LOOTTRACE 66+; **`DEV-DIAG:cook NO_JOBSITE job=cook/fuel`** |
| 837–842 | — | day=1 at last read, cook_unlocked, TOOK 10–12 each (past the old flake zone) |

Sweep A verdict: **0/16 in the old soft-lock signature** (TOOK=0 / COLLECTING freeze).
Two visible next-stall candidates: (a) kitchen_stocked ghost ACTIVE quests after completion,
(b) cook/fuel NO_JOBSITE. Neither seen as a hard stall — all observed worlds kept extracting.

## Sweep B (850+)
--- sweep B poll 19:33
850 | DEAD world=none cook_unlocked=0 LOOTTRACE=0) | TOOK=0 |  
851 | DEAD world=none cook_unlocked=0 LOOTTRACE=0) | TOOK=0 |  
852 | DEAD world=none cook_unlocked=0 LOOTTRACE=0) | TOOK=0 |  
853 | DEAD world=none cook_unlocked=0 LOOTTRACE=0) | TOOK=0 |  
854 | DEAD world=none cook_unlocked=0 LOOTTRACE=0) | TOOK=0 |  
855 | DEAD world=none cook_unlocked=0 LOOTTRACE=0) | TOOK=0 |  
--- sweep B poll 19:43
850 | alive world=day=1 cook_unlocked=1 LOOTTRACE=9) | TOOK=12 sees 1 quests | act:none | [19:43:33] [Server thread/INFO] [Questown:Villagers/]: Job changed to JobID{rootId='cook', jobI
851 | alive world=day=1 cook_unlocked=1 LOOTTRACE=8) | TOOK=11 sees 1 quests | act:none | [19:43:33] [Server thread/WARN] [ca.br.qu.QT/]: [DEV-DIAG:cook] NO_JOBSITE job=JobID{rootId='co
852 | alive world=day=1 cook_unlocked=1 LOOTTRACE=6) | TOOK=11 sees 1 quests | act:none | [19:43:33] [Server thread/WARN] [ca.br.qu.QT/]: [DEV-DIAG:kitchen] recipe=questown:kitchen_smal
853 | alive world=day=1 cook_unlocked=1 LOOTTRACE=8) | TOOK=13 sees 1 quests | act:none | [19:43:33] [Server thread/WARN] [ca.br.qu.QT/]: [DEV-DIAG:kitchen] recipe=questown:kitchen_smal
854 | alive world=day=1 cook_unlocked=1 LOOTTRACE=10) | TOOK=14 sees 1 quests | act:none | [19:43:33] [Server thread/WARN] [ca.br.qu.QT/]: [DEV-DIAG:kitchen] recipe=questown:kitchen_smal
855 | alive world=day=1 cook_unlocked=0 LOOTTRACE=0) | TOOK=1 sees 1 quests | act:none | [19:43:27] [Server thread/INFO] [Questown:Flag/]: [self_suff] [jcscan] executor sees 9 quests: 
--- sweep B poll 19:53
850 | alive world=day=2 cook_unlocked=1 LOOTTRACE=37) | TOOK=40 | sees 1 quests | ca.br.qu.QT/]: [DEV-DIAG:kitchen] recipe=questown:kitchen_stocked m
851 | alive world=day=2 cook_unlocked=1 LOOTTRACE=30) | TOOK=36 | sees 1 quests | Questown:Jobs/]: [chicken-arc] tick diag: spawned=false forfeit=tru
852 | alive world=day=2 cook_unlocked=1 LOOTTRACE=36) | TOOK=39 | sees 1 quests | ca.br.qu.QT/]: [DEV-DIAG:kitchen] recipe=questown:kitchen_stocked m
853 | DEAD world=day=2 cook_unlocked=1 LOOTTRACE=18) | TOOK=19 | sees 1 quests | minecraft/MinecraftServer]: ThreadedAnvilChunkStorage: All dimensio
854 | alive world=day=2 cook_unlocked=1 LOOTTRACE=39) | TOOK=40 | sees 1 quests | ca.br.qu.QT/]: [DEV-DIAG:cook] NO_JOBSITE job=JobID{rootId='cook', 
855 | alive world=day=2 cook_unlocked=0 LOOTTRACE=0) | TOOK=1 | sees 1 quests | Questown:Jobs/]: [chicken-arc] tick diag: spawned=false forfeit=tru
--- sweep B poll 20:04
850 | alive world=day=3 cook_unlocked=1 LOOTTRACE=63) | TOOK=57 | sees 1 quests | stuck=ROOM:questown:kitchen_stocked=ACTIVE
851 | alive world=day=3 cook_unlocked=1 LOOTTRACE=55) | TOOK=51 | sees 1 quests | stuck=ROOM:questown:block_room/block.questown.fishing_station=ACTIVE
852 | alive world=day=3 cook_unlocked=1 LOOTTRACE=65) | TOOK=54 | sees 1 quests | stuck=ROOM:questown:kitchen_stocked=ACTIVE
853 | DEAD world=day=2 cook_unlocked=1 LOOTTRACE=18) | TOOK=19 | sees 1 quests | stuck=ROOM:questown:block_room/block.questown.fishing_station=ACTIVE
854 | alive world=day=3 cook_unlocked=1 LOOTTRACE=63) | TOOK=61 | sees 1 quests | stuck=ROOM:questown:kitchen_stocked=ACTIVE
855 | alive world=day=3 cook_unlocked=0 LOOTTRACE=0) | TOOK=1 | sees 1 quests | stuck=LOOT:questown:villager_simple_furnace_food_raw=ACTIVE
856 | alive world=day=1 cook_unlocked=1 LOOTTRACE=10) | TOOK=14 | sees 1 quests | stuck=(none)
857 | alive world=day=1 cook_unlocked=1 LOOTTRACE=10) | TOOK=13 | sees 1 quests | stuck=(none)
858 | alive world=day=1 cook_unlocked=1 LOOTTRACE=12) | TOOK=15 | sees 1 quests | stuck=(none)
859 | alive world=day=1 cook_unlocked=1 LOOTTRACE=11) | TOOK=13 | sees 1 quests | stuck=(none)
860 | alive world=day=1 cook_unlocked=1 LOOTTRACE=12) | TOOK=14 | sees 1 quests | stuck=(none)
--- sweep B FINAL poll 20:14
850 | alive world=day=4 cook_unlocked=1 LOOTTRACE=63) | TOOK=61 | stuck=ROOM:questown:kitchen_stocked=ACTIVE | saved=0
851 | alive world=day=4 cook_unlocked=1 LOOTTRACE=55) | TOOK=53 | stuck=ROOM:questown:block_room/block.questown.fishing_station=ACTIVE | saved=0
852 | alive world=day=4 cook_unlocked=1 LOOTTRACE=65) | TOOK=56 | stuck=ROOM:questown:kitchen_stocked=ACTIVE | saved=0
854 | alive world=day=4 cook_unlocked=1 LOOTTRACE=63) | TOOK=63 | stuck=ROOM:questown:kitchen_stocked=ACTIVE | saved=0
855 | alive world=day=4 cook_unlocked=0 LOOTTRACE=0) | TOOK=1 | stuck=LOOT:questown:villager_simple_furnace_food_raw=ACTIVE | saved=0
856 | DEAD world=day=2 cook_unlocked=1 LOOTTRACE=19) | TOOK=17 | stuck=ROOM:questown:kitchen_stocked=ACTIVE | saved=1
857 | alive world=day=2 cook_unlocked=1 LOOTTRACE=40) | TOOK=39 | stuck=ROOM:questown:block_room/block.questown.fishing_station=ACTIVE | saved=0
858 | DEAD world=day=2 cook_unlocked=1 LOOTTRACE=20) | TOOK=17 | stuck=ROOM:questown:block_room/block.questown.fishing_station=ACTIVE | saved=1
859 | alive world=day=2 cook_unlocked=1 LOOTTRACE=36) | TOOK=36 | stuck=ROOM:questown:block_room/block.questown.fishing_station=ACTIVE | saved=0
860 | DEAD world=day=2 cook_unlocked=1 LOOTTRACE=21) | TOOK=16 | stuck=ROOM:questown:farmer_qt_harvest_wheat=ACTIVE | saved=1
