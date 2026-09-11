# Village Growth Held Run - Blocking Issue

## Current State
- Flag at 0, 64, 0
- 1 registered door at 6, 64, 4
- 1 room detected (but blocks tab not shown - room not matched by recipe)
- 1 container (chest) at 6, 64, 3
- Campfire quest completed
- Chicken stuck at WAITING_FOR_SIGN

## Blocking Issue
The chicken is waiting for a `SignBlock` item in the player's inventory, but:
1. The `place_board` command places a `JobBoardBlock`, not a `SignBlock`
2. The vanilla sign item/block is not available in this build
3. The `JobBoardBlock` extends `RoomBlock`, not `SignBlock`

The chicken-arc `requiredItemPredicate` for `WAITING_FOR_SIGN` checks:
```java
case WAITING_FOR_SIGN -> s -> blockItemBlock(s) instanceof SignBlock;
```

But the `place_board` command places a `JobBoardBlock` which is not a `SignBlock`.

## Additional Issue
The room is registered (rooms=1) but the blocks tab is not shown, which suggests the room is not matched by a recipe. The `isSignConvertedToJobBoard` method checks if a matched room contains a `JOB_BOARD_BLOCK`, but if the room is not matched, the chicken won't detect the `JOB_BOARD_BLOCK`.

## Open Quests
- job_board
- store_room
- special_quest.town_gate
- wooden_sword

## Villager
- 1 villager (gatherer) with unmet food need

## Attempted Actions
- Placed job board at 3, 64, 4; 5, 64, 5; 6, 64, 5 (all placed, chicken still waiting)
- Tried giving player sign item (unknown item)
- Tried giving player job_board_block item (chicken still waiting)
- Tried placing sign block with setblock (unknown block)

## Possible Next Steps
1. Check if the room is matched by looking at the room recipes
2. Try to bypass the chicken-arc beat
3. Check if the `JOB_BOARD_BLOCK` is inside the room's bounds
4. Try registering a new room with a door at a different position
