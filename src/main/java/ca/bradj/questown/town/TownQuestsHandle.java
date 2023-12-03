package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.TownRemoveQuestsContainer;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.town.interfaces.QuestsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCQuestBatch;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.rewards.AddBatchOfRandomQuestsForVisitorReward;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
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
            Optional<MCQuestBatch> first = town.quests.questBatches.getAllBatches()
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

    @Override
    public void showQuestsUI(ServerPlayer player) {
        @NotNull TownFlagBlockEntity t = unsafeGetTown();
        ImmutableList<HashMap.SimpleEntry<MCQuest, MCReward>> aQ = unsafeGetTown().getAllQuestsWithRewards();
        List<UIQuest> quests = UIQuest.fromLevel(t.getServerLevel(), aQ);

        NetworkHooks.openGui(player, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return new TownQuestsContainer(windowId, quests, t.getBlockPos());
            }
        }, data -> {
            TownQuestsContainer.write(data, quests, t.getBlockPos());
        });
    }

    private void showConfirmUI(ServerPlayer sp, MCQuestBatch batch) {
        final TownInterface t = unsafeGetTown();
        List<UIQuest> quests = UIQuest.fromLevel(sp.getLevel(), batch);
        NetworkHooks.openGui(sp, new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return TextComponent.EMPTY;
            }

            @Override
            public @NotNull AbstractContainerMenu createMenu(
                    int windowId,
                    @NotNull Inventory inv,
                    @NotNull Player p
            ) {
                return new TownRemoveQuestsContainer(windowId, quests, t.getTownFlagBasePos(), batch.getBatchUUID());
            }
        }, data -> {
            TownRemoveQuestsContainer.write(data, quests, t.getTownFlagBasePos(), batch.getBatchUUID());
        });
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
                    t.broadcastMessage(new TranslatableComponent("messages.town_flag.quest_batch_removed_1"));
                    t.broadcastMessage(new TranslatableComponent("messages.town_flag.quest_batch_removed_2"));
                }
                return;
            }
        }
    }

    @Override
    public ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> getAllQuestsWithRewards() {
        return town.getAllQuestsWithRewards();
    }
}
