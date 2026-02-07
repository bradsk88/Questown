# Questown Sequence Diagrams

This document provides visual explanations of the complex systems in Questown using Mermaid sequence diagrams.

---

## 1. Time Warp System

The Time Warp system simulates villager work during offline progression, allowing towns to advance without keeping chunks loaded.

### Overview: Player Returns After Offline Period

```mermaid
sequenceDiagram
    participant Player
    participant TownFlag as TownFlagBlockEntity
    participant Warper as ProductionTimeWarper
    participant WarpWI as TimeWarpWorldInteraction
    participant State as MCTownState

    Player->>TownFlag: warpTime(targetTicks)
    TownFlag->>TownFlag: readCurrentState()
    TownFlag->>State: snapshot immutable state

    loop Until targetTicks reached
        TownFlag->>Warper: getNextDaySegment(currentTick)
        Warper-->>TownFlag: checkpoint (6000, 11500, 22000, 24000)

        loop For each villager job
            TownFlag->>WarpWI: simulateJobCycle(villager, job)
            WarpWI->>State: read inventory & containers

            alt Needs supplies
                WarpWI->>Warper: simulateCollectSupplies()
                Warper->>State: remove from containers
                Warper->>State: add to villager inventory
            end

            alt Working
                WarpWI->>State: decrement workLeft
                WarpWI->>WarpWI: degradeTool() if applicable
            end

            alt Extraction ready
                WarpWI->>WarpWI: runPreExtractHooks()
                WarpWI->>Warper: simulateExtractProduct()
                Warper->>State: add products to inventory
            end

            alt Drop loot
                WarpWI->>Warper: simulateDropLoot()
                Warper->>State: move items to containers
            end

            WarpWI->>State: advance processingState
        end
    end

    TownFlag->>TownFlag: applyFinalState(State)
    TownFlag-->>Player: Warp complete
```

### Detailed: Single Job Cycle During Warp

```mermaid
sequenceDiagram
    participant WarpWI as TimeWarpWorldInteraction
    participant JobState as State{processingState, ingredientCount, workLeft}
    participant Hooks as SpecialRules
    participant Containers as TownContainers

    Note over WarpWI: Phase 1: Ingredient Gathering
    WarpWI->>Containers: findMatchingItems(recipe.ingredients)
    Containers-->>WarpWI: available items
    WarpWI->>JobState: incrementIngredientCount()
    WarpWI->>Hooks: PostInsertHook.run()

    Note over WarpWI: Phase 2: Work Execution
    loop While workLeft > 0
        WarpWI->>JobState: workLeft -= workSpeed
        WarpWI->>WarpWI: getAffectedTime(effects)
    end

    Note over WarpWI: Phase 3: Product Extraction
    WarpWI->>Hooks: PreExtractHook.run()
    Hooks-->>WarpWI: modified products (optional)
    WarpWI->>JobState: generateProducts(recipe.outputs)

    Note over WarpWI: Phase 4: Drop Loot
    WarpWI->>Containers: dropIntoContainers(products)
    Containers-->>WarpWI: success/overflow
    WarpWI->>Hooks: PostDropHook.run()

    WarpWI->>JobState: advanceToNextState()
```

---

## 2. Declarative Job Ticker (State Machine)

The job ticker orchestrates villager work through a multi-phase state machine.

### Job State Machine Flow

```mermaid
sequenceDiagram
    participant Entity as VillagerEntity
    participant Ticker as DeclarativeJobTicker
    participant Logic as JobLogic
    participant World as WorldInteraction
    participant Hooks as SpecialRules

    Entity->>Ticker: tick(dependencies)

    Note over Ticker: Pre-tick Setup
    Ticker->>Ticker: computeRoomsNeedingInput()
    Ticker->>Hooks: runPreTickHook()
    Ticker->>Ticker: computeRoomsWithCompletedProduct()

    Ticker->>Logic: tick()
    Logic->>Logic: computeState() → ProductionStatus

    alt Status == COLLECTING_SUPPLIES
        Logic->>World: tryGetSupplies()
        World->>World: findNearestContainer()
        World->>World: removeItem(ingredient)
        World-->>Logic: item collected
        Logic->>Logic: incrementIngredientCount()
    end

    alt isWorkingOnProduction() (job-specific state 0-9)
        Logic->>World: getWorkSpot()
        World-->>Logic: currentJobSite
        Logic->>Logic: workLeft -= workSpeed

        alt workLeft == 0
            Logic->>Logic: status = EXTRACTING_PRODUCT
        end
    end

    alt Status == EXTRACTING_PRODUCT
        Logic->>Hooks: PreExtractHook.run()
        Logic->>World: extractProduct()
        World-->>Logic: generated items
        Logic->>Logic: addToInventory(items)
    end

    alt Status == DROPPING_LOOT
        Logic->>World: tryDropLoot()
        World->>World: findResultContainer()
        World->>World: insertItem(product)
        Logic->>Hooks: PostDropHook.run()
    end

    Logic-->>Ticker: stateUpdated
    Ticker-->>Entity: tick complete
```

### State Progression Detail

```mermaid
sequenceDiagram
    participant State as JobState
    participant Phase as ProcessingPhase

    Note over State: Initial State
    State->>State: processingState = 0
    State->>State: ingredientCount = 0
    State->>State: workLeft = maxWork * 10

    loop For each processing state (0 to maxState)
        Note over Phase: Ingredient Phase
        loop Until ingredientCount == required
            Phase->>State: ingredientCount++
            Phase->>Phase: PostInsertHook()
        end

        Note over Phase: Work Phase
        loop Until workLeft == 0
            Phase->>State: workLeft -= speed
        end

        alt processingState < maxState
            Phase->>State: processingState++
            Phase->>State: ingredientCount = 0
            Phase->>State: workLeft = nextWork * 10
        else processingState == maxState
            Phase->>Phase: PreExtractHook()
            Phase->>State: extractProducts()
            Phase->>State: dropLoot()
            Phase->>State: reset to state 0
        end
    end
```

---

## 3. Special Rules / Hook System

Hooks allow jobs to integrate with Minecraft world state beyond the declarative model.

### Hook Invocation Flow

```mermaid
sequenceDiagram
    participant Ticker as DeclarativeJobTicker
    participant Registry as SpecialRulesRegistry
    participant Hook as HookRunner
    participant Modifier as JobPhaseModifier
    participant World as ServerLevel

    Note over Ticker: During Job Execution
    Ticker->>Registry: getRuleAppliers(job.specialRules)
    Registry-->>Ticker: List<JobPhaseModifier>

    Note over Ticker: Pre-Tick Hook
    Ticker->>Hook: runPreTickHook()
    loop Each modifier
        Hook->>Modifier: beforeTick(context)
    end

    Note over Ticker: After Insert Item
    Ticker->>Hook: PostInsertHook.run()
    loop Each modifier
        Hook->>Modifier: afterInsertItem(context, event)
        Modifier->>World: modifyBlockState() (optional)
        Modifier->>World: spawnEffects() (optional)
    end

    Note over Ticker: Before Extract
    Ticker->>Hook: PreExtractHook.run()
    loop Each modifier
        Hook->>Modifier: beforeExtract(context, event)
        Modifier->>event: itemAcceptor.accept(bonusItems)
        Modifier->>World: updateBlock() (optional)
    end

    Note over Ticker: After Drop Loot
    Ticker->>Hook: PostDropHook.run()
    loop Each modifier
        Hook->>Modifier: afterDropLoot(context, event)
        Modifier->>World: cleanup() (optional)
    end
```

### Example: Cook Job with Furnace Integration

```mermaid
sequenceDiagram
    participant Cook as CookVillager
    participant Ticker as JobTicker
    participant FurnaceRule as FurnaceSpecialRule
    participant Furnace as FurnaceBlockEntity

    Cook->>Ticker: tick() with coal + food
    Ticker->>Ticker: ingredient phase

    Note over Ticker: Insert fuel into real furnace
    Ticker->>FurnaceRule: afterInsertItem(coal)
    FurnaceRule->>Furnace: setItem(FUEL_SLOT, coal)

    Note over Ticker: Insert food into real furnace
    Ticker->>FurnaceRule: afterInsertItem(rawFood)
    FurnaceRule->>Furnace: setItem(INPUT_SLOT, rawFood)
    Furnace->>Furnace: startCooking()

    Note over Ticker: Work phase (waiting)
    Ticker->>Ticker: workLeft decrements

    Note over Ticker: Extract from real furnace
    Ticker->>FurnaceRule: beforeExtract()
    FurnaceRule->>Furnace: getItem(OUTPUT_SLOT)
    Furnace-->>FurnaceRule: cookedFood
    FurnaceRule->>Ticker: itemAcceptor.accept(cookedFood)

    Ticker->>Cook: inventory += cookedFood
```

---

## 4. Job Assignment System

Jobs are automatically assigned to villagers based on town needs and player requests.

### Job Selection Algorithm

```mermaid
sequenceDiagram
    participant Town as TownFlagBlockEntity
    participant Villagers as TownVillagers
    participant Registry as ServerJobsRegistry
    participant JobBoard as WorkRequests

    Town->>Villagers: assignNextJob(villager)
    Villagers->>Villagers: getPreferredWork(currentJob, predicates)

    Villagers->>Registry: getPreferredWorkIds(currentJob)
    Registry-->>Villagers: shuffled job list

    loop For each preferred job
        Villagers->>Registry: canAlwaysStart(villagerID, job)?
        alt Job can always start
            Villagers-->>Town: return job
        end

        Villagers->>Registry: canFit(villagerID, job, currentTick)?
        alt Job too long for remaining day
            Villagers->>Villagers: skip job
        end

        Villagers->>JobBoard: getRequestedResults()
        JobBoard-->>Villagers: player requests

        loop For each request
            Villagers->>Registry: canSatisfy(townData, job, request)
            alt Job can produce requested item
                Villagers-->>Town: return job
            end
        end
    end

    Villagers-->>Town: null (no suitable job)
    Town->>Town: assign fallback (WorkSeekerJob)
```

### Request-Based Job Priority

```mermaid
sequenceDiagram
    participant Player
    participant JobBoard as JobBoard/Clipboard
    participant Villager
    participant Assignment as JobAssignment

    Player->>JobBoard: request(ironSword)
    JobBoard->>JobBoard: store request

    Note over Villager: Job cycle completes
    Villager->>Assignment: getNextJob()

    Assignment->>JobBoard: getRequestedResults()
    JobBoard-->>Assignment: [ironSword]

    Assignment->>Assignment: findJobProducing(ironSword)
    Note over Assignment: Check: Blacksmith → ironSword
    Assignment->>Assignment: hasIngredients(blacksmith)?

    alt Ingredients available
        Assignment-->>Villager: assign Blacksmith
    else Missing ingredients
        Assignment->>Assignment: findJobProducing(ironIngot)
        Assignment-->>Villager: assign Smelter
    end
```

---

## 5. Realtime vs Warp: Shared Logic Flow

This diagram shows how realtime and warp share the core job logic but differ in world interaction.

```mermaid
sequenceDiagram
    participant RT as RealtimeWorldInteraction
    participant Warp as TimeWarpWorldInteraction
    participant Shared as DeclarativeJobTicker
    participant RTWorld as Minecraft World
    participant WarpState as MCTownState

    Note over RT,Warp: Both use same ticker logic

    rect rgb(200, 230, 200)
        Note over RT: REALTIME PATH
        RT->>Shared: tick()
        Shared->>RT: getWorkSpot()
        RT->>RTWorld: getBlockEntity(pos)
        RTWorld-->>RT: real block
        Shared->>RT: tryInsertItem()
        RT->>RTWorld: insertItem(slot, item)
        RT->>RTWorld: playSound()
    end

    rect rgb(200, 200, 230)
        Note over Warp: WARP PATH
        Warp->>Shared: tick()
        Shared->>Warp: getWorkSpot()
        Warp->>WarpState: getJobSiteState(pos)
        WarpState-->>Warp: simulated state
        Shared->>Warp: tryInsertItem()
        Warp->>WarpState: updateState(pos, item)
        Note over Warp: No sound, no entity movement
    end

    Note over Shared: Same state transitions
    Shared->>Shared: advanceProcessingState()
    Shared->>Shared: resetForNextCycle()
```

---

## Appendix: Key Classes Reference

| System | Class | File Location |
|--------|-------|---------------|
| Time Warp | `ProductionTimeWarper` | `jobs/ProductionTimeWarper.java` |
| Time Warp | `TimeWarpWorldInteraction` | `jobs/declarative/TimeWarpWorldInteraction.java` |
| Job Ticker | `DeclarativeJobTicker` | `jobs/declarative/DeclarativeJobTicker.java` |
| Job Ticker | `DeclarativeJob` | `jobs/DeclarativeJob.java` |
| Hooks | `JobPhaseModifier` | `integration/jobs/JobPhaseModifier.java` |
| Hooks | `PreExtractHook` | `jobs/declarative/PreExtractHook.java` |
| Hooks | `VanillaSpecialRules` | `_vanilla/VanillaSpecialRules.java` |
| Assignment | `TownVillagers` | `town/TownVillagers.java` |
| Assignment | `WorksBehaviour` | `jobs/WorksBehaviour.java` |
