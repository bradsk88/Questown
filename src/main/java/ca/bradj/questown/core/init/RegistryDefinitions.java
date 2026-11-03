package ca.bradj.questown.core.init;

import ca.bradj.questown.core.init.items.ItemsInit;

/**
 * Loads every {@code *Init} class so its values register with the loader. Blocks come before the
 * items, block entities and entities that refer to them.
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
    }
}
