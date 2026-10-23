package ca.bradj.questown.gui;

/**
 * The villager menu's Block-of-Progress tab. Opened from the flag's villagers tab (the
 * management click-through, no inventory tab), it is always shown and leads back to the flag
 * UI's BOP tab (dev, 2026-10-19 playtest). Opened by interacting with the villager, it is the
 * villager's own BOP screen (with attention) when the villager has earned one, and otherwise
 * also leads to the flag UI's BOP tab, so a player asked to "insert a Block of Progress" can find
 * one (agent playtest r10 #5, dev trial 2026-10-23).
 */
public record VillagerBopTab(boolean shown, boolean opensFlagUi, boolean attention) {

    public static VillagerBopTab decide(
            boolean openedFromFlag,
            boolean villagerHasBop
    ) {
        if (villagerHasBop && !openedFromFlag) {
            return new VillagerBopTab(true, false, true);
        }
        return new VillagerBopTab(true, true, false);
    }
}
