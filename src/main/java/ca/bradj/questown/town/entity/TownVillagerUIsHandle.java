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
        ImmutableSet<JobID> unlockable = unlockableSkills(villagerId);
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
        ImmutableSet<JobID> unlockable = unlockableSkills(villagerId);
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

    /** The skills tab's nodes for this villager: known children of its job that its level allows, unlocked ones included. Empty at level 1, which hides the tab. */
    public ImmutableSet<JobID> unlockableSkills(UUID villagerId) {
        VisitorMobEntity entity = villagers.getEntity(villagerId);
        if (entity == null) {
            return ImmutableSet.of();
        }
        int level = villagers.getLevel(villagerId);
        return villagers
                .getChildJobsKnownToExist(entity.getJobId())
                .stream()
                .filter(job -> ServerJobsRegistry.getAvailableAtLevel(job) <= level)
                .collect(ImmutableSet.toImmutableSet());
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
