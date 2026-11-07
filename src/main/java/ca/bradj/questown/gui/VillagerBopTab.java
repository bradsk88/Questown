package ca.bradj.questown.gui;

/**
 * The villager menu's Blocks of Progress tab. From the flag, it leads back to the flag's tab. From
 * the villager, it is the villager's own tab when it has earned one, and otherwise also leads to
 * the flag's.
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
