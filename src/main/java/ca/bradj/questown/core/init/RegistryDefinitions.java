package ca.bradj.questown.core.init;

import ca.bradj.questown.core.init.items.ItemsInit;

/**
 * Neutral "define all registry values" hook, called from the per-loader {@code @Mod} entry (via
 * {@code Loader.defineRegistries()}) before the created registries are bound to the mod bus.
 *
 * <p>It touches each {@code *Init} class so its static initialisers run — which register the values with the
 * active loader's registry. Kept in main (neutral) so the set of registered domains lives with the mod, not
 * the loader.
 *
 * <p>Ordering matters: blocks are defined before the items/tiles/entities that reference a block's holder.
 */
public final class RegistryDefinitions {
    private RegistryDefinitions() {
    }

    public static void define() {
        BlocksInit.boot();
        MenuTypesInit.boot();
        TilesInit.boot();
        ItemsInit.boot();
        EntitiesInit.boot();
        ca.bradj.questown._vanilla.entities.EntitiesInit.boot();
        RewardsInit.boot();
        ScheduleInit.boot();
        CommandsInit.boot();
        AdvancementsInit.register();
    }
}
