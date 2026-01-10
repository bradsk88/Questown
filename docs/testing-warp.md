# Time Warp Manual Testing Guide

This document provides step-by-step test scenarios for validating the time warp feature.

## Prerequisites

Before testing, ensure:
- Creative mode enabled
- Town flag placed and initialized
- At least one job room built (e.g., town gate for gatherer)
- Supplies available in containers (e.g., carrots in store room)
- At least one villager spawned with a job

## Debug Commands

| Command | Description |
|---------|-------------|
| `/qt debug warp <pos> <ticks>` | Warp time by specified ticks |
| `/qt debug warp <pos> <ticks> verbose` | Warp with detailed logging |
| `/qt debug warp-status <pos>` | Show current warp-ready state |
| `/qt debug log <pos>` | Log full town state |

## Test Scenarios

### Scenario A: Basic Gatherer Warp

**Setup:**
- [ ] Town flag at known position (note: `____`)
- [ ] Town gate room built with welcome mat
- [ ] Carrots (or other supplies) in store room chest
- [ ] Villager with gatherer job assigned

**Test Steps:**
1. [ ] Run `/qt debug log <flag-pos>` - capture before state
2. [ ] Note villager inventory contents
3. [ ] Note container contents
4. [ ] Note work block states
5. [ ] Run `/qt debug warp <flag-pos> 1000`
6. [ ] Check: Did villager inventory change?
7. [ ] Check: Did container contents change?
8. [ ] Check: Did work block state progress?
9. [ ] Run `/qt debug log <flag-pos>` - compare after state

**Expected Results:**
- Villager should have collected supplies OR dropped results
- Work block timers should have decreased
- Container contents should reflect item movement

---

### Scenario B: Timer-Based Work

**Setup:**
- [ ] Job with timer requirement configured
- [ ] Supplies available for the job
- [ ] Villager assigned to the job

**Test Steps:**
1. [ ] Note current work block state and timer
2. [ ] Run `/qt debug warp <flag-pos> 500` (less than timer)
3. [ ] Check: Timer should have decreased but not completed
4. [ ] Note remaining timer value
5. [ ] Run `/qt debug warp <flag-pos> <remaining>` (exact remaining time)
6. [ ] Check: Work block state should have advanced

**Expected Results:**
- Timer decreases proportionally to ticks warped
- State advances only when timer reaches zero

---

### Scenario C: Verbose Warp Logging

**Purpose:** Verify detailed logging works for debugging.

**Test Steps:**
1. [ ] Run `/qt debug warp <flag-pos> 500 verbose`
2. [ ] Check server logs for:
   - [ ] "Verbose warp logging enabled"
   - [ ] Important tick counts per villager
   - [ ] Total warp steps
   - [ ] "[WARP] tick=... status=..." entries
   - [ ] "Verbose warp logging disabled"

---

### Scenario D: Warp Status Command

**Purpose:** Verify warp-status shows relevant information.

**Test Steps:**
1. [ ] Run `/qt debug warp-status <flag-pos>`
2. [ ] Check output includes:
   - [ ] Villager UUIDs and jobs
   - [ ] Villager inventory contents
   - [ ] Work block states
   - [ ] Container summaries

---

## Edge Case Tests

### E1: Full Inventory
- [ ] Fill villager inventory with items
- [ ] Warp time
- [ ] Verify: Villager should drop loot before collecting new supplies

### E2: No Supplies Available
- [ ] Empty all containers of required supplies
- [ ] Warp time
- [ ] Verify: Villager enters NO_SUPPLIES status, no crash

### E3: No Job Site
- [ ] Remove villager's job site room
- [ ] Warp time
- [ ] Verify: Villager enters NO_JOBSITE status, no crash

### E4: Container Full
- [ ] Fill all containers completely
- [ ] Give villager items to drop
- [ ] Warp time
- [ ] Verify: Items remain in villager inventory (NO_SPACE status)

### E5: Very Long Warp
- [ ] Run `/qt debug warp <flag-pos> 24000` (full Minecraft day)
- [ ] Verify: Completes without crash
- [ ] Verify: Multiple work cycles completed

### E6: No Work Possible
- [ ] Have villager at job board with no matching jobs available
- [ ] Warp time
- [ ] Verify: NO_WORK_POSSIBLE status, no crash

---

## Recovery Tests

### R1: World Reload After Warp
1. [ ] Perform a warp
2. [ ] Save and reload world
3. [ ] Verify: State persisted correctly
4. [ ] Verify: Villagers restored with correct inventories

### R2: Warp After Failed Warp
1. [ ] Force a warp failure (if possible via debug)
2. [ ] Attempt another warp
3. [ ] Verify: System recovers gracefully

---

## Troubleshooting

### Warp completes instantly (0ms)
- Check if villagers have jobs assigned
- Check if important ticks were computed (enable verbose mode)
- Verify supplies are available

### NullPointerException during warp
- Check for null job IDs (fixed in NO_WORK_POSSIBLE handler)
- Check ImportantTicks.forVillager() null handling
- Enable verbose mode to identify which villager/step fails

### No state changes after warp
- Verify villager has work to do
- Check work block states with `/qt debug warp-status`
- Verify handler dispatch logs in verbose mode
