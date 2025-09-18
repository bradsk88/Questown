package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.FlagMenus;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.TownRemoveQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.UnsafeTown;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.rewards.AddBatchOfQuestsForVisitorReward;
import ca.bradj.questown.town.rewards.AddRandomUpgradeQuest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TownQuestsHandle implements QuestsHolder {

    private UnsafeTown town = new UnsafeTown(TownQuestsHandle.class);

    public void initialize(TownFlagBlockEntity t) {
        this.town.initialize(t);
    }

    /**
     * Only safe to call after initialize
     * @deprecated Use this.town directly.
     */
    private @NotNull TownFlagBlockEntity unsafeGetTown() {
        return town.getUnsafe();
    }

    @Override
    public void requestRemovalOfQuestAtIndex(
            UUID batchID,
            ServerPlayer sender,
            boolean promptUser
    ) {
        if (promptUser) {
            Optional<MCQuestBatch> first = unsafeGetTown().quests.questBatches.getAllBatches()
                                                                              .stream()
                                                                              .filter(v -> batchID.equals(v.getBatchUUID()))
                                                                              .findFirst();
            if (first.isEmpty()) {
                QT.QUESTS_LOGGER.error("Received request to remove non-existent batch. Doing nothing. [{}]", batchID);
                return;
            }
            showConfirmUI(sender, first.get());
        } else {
            doRemove(batchID, sender);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void showQuestsUI(ServerPlayer player) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        ImmutableList<HashMap.SimpleEntry<MCQuest, MCReward>> aQ = unsafeGetTown().getAllQuestsWithRewards();
        @SuppressWarnings("DataFlowIssue") List<UIQuest> quests = UIQuest.fromLevel(t.getServerLevel(), aQ);

        Collection entities = t.getVillagerHandle().entities();
        Compat.openScreen(
                player, new MenuProvider() {
                    @Override
                    public @NotNull Component getDisplayName() {
                        return Compat.literal("");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(
                            int windowId,
                            @NotNull Inventory inv,
                            @NotNull Player p
                    ) {
                        return new TownQuestsContainer(
                                windowId,
                                quests,
                                t.getInfo(),
                                () -> triggerAdvancement(player, t)
                        );
                    }

                    private void triggerAdvancement(
                            ServerPlayer player,
                            @NotNull TownFlagBlockEntity t
                    ) {
                        if (t.getVillagerHandle().entities().isEmpty()) {
                            return;
                        }
                        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(
                                player.getLevel(),
                                RoomTrigger.Triggers.FirstOpenFlagMenu,
                                t.getBlockPos()
                        );
                    }
                }, data ->
                        FlagMenus.writeAndLink(data, quests, t.getInfo(), player, entities, t.bopCount)
        );
    }

    @Override
    public List<AbstractMap.SimpleEntry<MCQuest, MCReward>> getQuestsWithRewardsForVillager(UUID uuid) {
        return unsafeGetTown().quests.getAllForVillagerWithRewards(uuid);
    }

    @Override
    public ImmutableSet<VillagerUUID> getVillagersWithQuests() {
        return TownQuests.getVillagers(unsafeGetTown().quests);
    }

    @Override
    public Collection<MCQuest> getQuestsForVillager(UUID uuid) {
        return unsafeGetTown().quests.getAllForVillager(VillagerUUID.from(uuid));
    }

    @Override
    public void addBatchOfQuestsForVisitor(@Nullable VillagerUUID visitorUUID) {
        TownFlagBlockEntity t = unsafeGetTown();
        TownQuests.addBatchForVisitor(t, t.quests, visitorUUID);
        t.setChanged();
    }

    @Override
    public void addRandomUpgradeQuestForVisitor(VillagerUUID visitorUUID) {
        TownFlagBlockEntity t = unsafeGetTown();
        TownQuests.addUpgradeQuest(t, t.quests, visitorUUID);
        t.setChanged();
    }

    @Override
    public void addItemQuest(
            ResourceLocation itemId,
            int count
    ) {
        TownFlagBlockEntity t = unsafeGetTown();
        TownQuests.addItemQuest(t, t.quests, itemId, count);
    }

    @Override
    public Collection<MCQuestBatch> getAllBatchesForVillager(UUID uuid) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        return t.quests.getBatches().stream().filter(
                v -> uuid.equals(v.getOwner())
        ).toList();
    }

    private void showConfirmUI(
            ServerPlayer sp,
            MCQuestBatch batch
    ) {
        final TownInterface t = unsafeGetTown();
        List<UIQuest> quests = UIQuest.fromLevel(sp.getLevel(), batch);
        Compat.openScreen(
                sp, new MenuProvider() {
                    @Override
                    public @NotNull Component getDisplayName() {
                        return Compat.literal("");
                    }

                    @Override
                    public @NotNull AbstractContainerMenu createMenu(
                            int windowId,
                            @NotNull Inventory inv,
                            @NotNull Player p
                    ) {
                        return new TownRemoveQuestsContainer(
                                windowId,
                                quests,
                                t.getTownFlagBasePos(),
                                batch.getBatchUUID()
                        );
                    }
                }, data ->
                        TownRemoveQuestsContainer.write(data, quests, t.getTownFlagBasePos(), batch.getBatchUUID())
        );
    }

    private void doRemove(
            UUID batchID,
            ServerPlayer sender
    ) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        for (MCQuestBatch b : t.quests.getBatches()) {
            if (batchID.equals(b.getBatchUUID())) {
                if (t.quests.questBatches.decline(b)) {
                    t.quests.playerDiscardedLastBatch = true;
                    QT.QUESTS_LOGGER.debug("Quest batch removed: {}", b);
                    addReplacementQuestBatch(t, b);
                    t.setChanged();
                    if (!t.getAllQuests().isEmpty()) {
                        showQuestsUI(sender);
                    } else {
                        sender.closeContainer();
                    }
                    t.messages.batchRemoved();
                }
                return;
            }
        }
    }

    private void addReplacementQuestBatch(
            @NotNull TownFlagBlockEntity t,
            MCQuestBatch b
    ) {
        if (Compat.getRandomBool(t.getServerLevel())) {
            t.addMorningReward(new AddBatchOfQuestsForVisitorReward(t, null));
            return;
        }

        if (b.getOwner() != null) {
            t.addMorningReward(new AddRandomUpgradeQuest(t, b.getOwner()));
            return;
        }

        QT.QUESTS_LOGGER.error("Quest batch owner was null, assigning next batch to someone else.");
        UUID owner = t.getRandomVillager();
        if (owner != null) {
            t.addMorningReward(new AddRandomUpgradeQuest(t, owner));
            return;
        }

        QT.QUESTS_LOGGER.error("No villagers to assign next upgrade quest batch to, falling back to randdom");
        t.addMorningReward(new AddBatchOfQuestsForVisitorReward(t, null));
    }

    @Override
    public ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards() {
        return unsafeGetTown().getAllQuestsWithRewards();
    }


}
