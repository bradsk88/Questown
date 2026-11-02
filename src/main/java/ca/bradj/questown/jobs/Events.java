package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.DeclarativeJobs;
import ca.bradj.questown.town.entity.TownVillagerHandle;

/** Job setup that runs once, at common setup. */
public final class Events {
    private Events() {
    }

    public static void onCommonSetup() {
        DeclarativeJobs.staticInitialize();
        TownVillagerHandle.staticInit();
    }
}
