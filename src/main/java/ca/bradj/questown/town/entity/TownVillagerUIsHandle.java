package ca.bradj.questown.town.entity;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownVillagerUIs;
import ca.bradj.questown.town.UnsafeTown;
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
        TownVillagerUIs.showUI(
                sender,
                villagers.entities(),
                type,
                villagerId,
                villagers.learning.getUnlockedJobs(),
                villagers.learning.getChildJobsKnownToExist(villagers.getEntity(villagerId).getJobId())
        );
    }

    public void showMultiStatusUI(ServerPlayer player, Collection<VisitorMobEntity> entities) {
        TownVillagerUIs.showMultiStatusUI(
                player,
                town.getUnsafe().getInfo(),
                entities,
                () -> town.getUnsafe().getAllQuestsWithRewards(),
                town.getUnsafe().getBlocksOfProgress()
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
