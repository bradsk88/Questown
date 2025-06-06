package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.gui.FlagMenus;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.TownRemoveQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TownQuestsHandle implements QuestsHolder {
    @Nullable
    private TownFlagBlockEntity town;

    public void initialize(TownFlagBlockEntity t) {
        this.town = t;
    }

    /**
     * Only safe to call after initialize
     */
    private @NotNull TownFlagBlockEntity unsafeGetTown() {
        if (town == null) {
            throw new IllegalStateException("Town has not been initialized on quest handle yet");
        }
        return town;
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
    public ImmutableSet<UUID> getVillagersWithQuests() {
        return TownQuests.getVillagers(unsafeGetTown().quests);
    }

    @Override
    public Collection<MCQuest> getQuestsForVillager(UUID uuid) {
        return unsafeGetTown().quests.getAllForVillager(uuid);
    }

    @Override
    public void addBatchOfRandomQuestsForVisitor(@Nullable UUID visitorUUID) {
        TownFlagBlockEntity t = unsafeGetTown();
        TownQuests.addRandomBatchForVisitor(t, t.quests, visitorUUID);
        t.setChanged();
    }

    @Override
    public void addRandomUpgradeQuestForVisitor(UUID visitorUUID) {
        TownFlagBlockEntity t = unsafeGetTown();
        TownQuests.addUpgradeQuest(t, t.quests, visitorUUID);
        t.setChanged();
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
                    QT.QUESTS_LOGGER.debug("Quest batch removed: {}", b);
                    t.addMorningReward(new AddBatchOfRandomQuestsForVisitorReward(t, b.getOwner()));
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

    @Override
    public ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards() {
        return unsafeGetTown().getAllQuestsWithRewards();
    }


}
