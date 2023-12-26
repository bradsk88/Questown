package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.town.TownState;
import com.google.common.collect.ImmutableList;

public interface Snapshot<H extends HeldItem<H, ?>> {
    String statusStringValue();

    String jobStringValue();

    ImmutableList<H> items();

    JobID jobId();

    AbstractWorldInteraction<TownState<?, ?, ?>, ?, ?, H> getTownStateWI(TownState<?, ?, ?> storedState);
}
