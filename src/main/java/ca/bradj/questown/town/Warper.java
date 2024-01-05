package ca.bradj.questown.town;

public interface Warper<LOOT_SOURCE, TOWN extends TownState<?, ?, ?, ?, ?>> {
    TOWN warp(
            LOOT_SOURCE lootSource,
            TOWN liveState,
            long currentTick,
            long ticksPassed,
            // This was added because I didn't want to store every single block state
            // for every single block in the village. So, instead, we use this number
            // to designate a fake position to each villager, and we pretend that
            // there is a block at that position containing the villager's job block.
            // This is a heuristic approach to make the time warp easier to implement.
            int villagerNum
    );
}
