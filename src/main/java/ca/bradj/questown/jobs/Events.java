package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.DeclarativeJobs;
import ca.bradj.questown.town.entity.TownVillagerHandle;

/**
 * Loader-neutral common setup for jobs.
 *
 * <p>The per-loader mod-bus binder calls {@link #onCommonSetup()} inside its {@code FMLCommonSetupEvent}
 * work, so this body runs once the loader's setup queue drains.
 */
public final class Events {
    private Events() {
    }

    public static void onCommonSetup() {
        DeclarativeJobs.staticInitialize();
        TownVillagerHandle.staticInit();
    }
}
