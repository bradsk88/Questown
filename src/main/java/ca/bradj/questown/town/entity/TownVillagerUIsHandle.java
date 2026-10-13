package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownVillagerUIs;
import ca.bradj.questown.town.UnsafeTown;
import com.google.common.collect.ImmutableSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;
import java.util.UUID;

public class TownVillagerUIsHandle {
    private final SimpleVillagerHandle<?, VisitorMobEntity> villagers;
    private final UnsafeTown town = new UnsafeTown(getClass());

    public TownVillagerUIsHandle(SimpleVillagerHandle<?, VisitorMobEntity> villagers) {
        this.villagers = villagers;
    }

    public void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId
    ) {
        // Skills-tab gate (2026-10-13 ruling): a job is only unlockable - and so only shown
        // in the skills tab - once the townie's level reaches its JSON available_at_level.
        // At the starting level (1) the set is empty, which is what hides the tab.
        int level = villagers.getLevel(villagerId);
        ImmutableSet<JobID> unlockable = villagers
                .getChildJobsKnownToExist(villagers.getEntity(villagerId).getJobId())
                .stream()
                .filter(job -> ServerJobsRegistry.getAvailableAtLevel(job) <= level)
                .collect(ImmutableSet.toImmutableSet());
        TownVillagerUIs.showUI(
                sender,
                villagers.entities(),
                type,
                villagerId,
                villagers.getUnlockedJobs(),
                unlockable
        );
    }

    public void showUI(
            ServerPlayer sender,
            String type,
            UUID villagerId,
            boolean showInventoryTab
    ) {
        int level = villagers.getLevel(villagerId);
        ImmutableSet<JobID> unlockable = villagers
                .getChildJobsKnownToExist(villagers.getEntity(villagerId).getJobId())
                .stream()
                .filter(job -> ServerJobsRegistry.getAvailableAtLevel(job) <= level)
                .collect(ImmutableSet.toImmutableSet());
        TownVillagerUIs.showUI(
                sender,
                villagers.entities(),
                type,
                villagerId,
                villagers.getUnlockedJobs(),
                unlockable,
                showInventoryTab
        );
    }

    public void showMultiStatusUI(ServerPlayer player, Collection<VisitorMobEntity> entities) {
        TownVillagerUIs.showMultiStatusUI(
                player,
                town.getUnsafe().getInfo(),
                entities,
                () -> town.getUnsafe().getAllQuestsWithRewards(),
                town.getUnsafe().depositedBopRootJobs(),
                () -> town.getUnsafe().hasVillagerArrivingInMorning()
        );
    }

    public void showItemJobsUI(
            ServerPlayer sender,
            Ingredient itemToShowJobsFor,
            Collection<VisitorMobEntity> entities
    ) {
        TownVillagerUIs.showItemJobsUI(sender, town.getUnsafe(), entities, itemToShowJobsFor);
    }

    public void showJobUI(
            ServerPlayer sender,
            JobID jobToShow,
            Collection<VisitorMobEntity> entities
    ) {
        TownVillagerUIs.showJobsWithSameRootUI(sender, town.getUnsafe(), entities, jobToShow);
    }

    public void init(TownFlagBlockEntity t) {
        town.initialize(t);
    }
}
