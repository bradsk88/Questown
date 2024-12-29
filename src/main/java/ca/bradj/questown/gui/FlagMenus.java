package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.MultiStatusScreenSyncMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.StatusListener;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class FlagMenus {
    TownQuestsContainer questsMenu;
    MultiStatusMenu villagersMenu;
    TownEconomicsMenu econMenu;

    public FlagMenus() {
    }

    public static FlagMenus fromNetwork(
            int windowId,
            Player player,
            FriendlyByteBuf buf
    ) {
        try {
            // Buffer reads - order must match write()
            Collection<UIQuest> quests = VillagerQuestsContainer.readQuests(buf);
            BlockPos flagPos = VillagerQuestsContainer.readFlagPos(buf);

            FlagMenus menus = new FlagMenus();
            // Never provide these initializers with the entity, itself. Instead, pass the entity's UUID.
            // It tends to cause client-side-only bugs that don't show up in the dev environment.
            menus.initQuestsMenuClientSide(windowId, quests, flagPos);
            menus.initMultiVillagerStatusMenuClientSide(windowId, flagPos);
            menus.initEconClientSide(windowId, flagPos);
            return menus;
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to open town quests container: {}", e.getMessage());
            throw e;
        }
    }

    public static void writeAndLink(
            FriendlyByteBuf data,
            List<UIQuest> quests,
            BlockPos flagPos,
            ServerPlayer player,
            Iterable<? extends VisitorMobEntity> es
    ) {
        VillagerQuestsContainer.write(data, quests, flagPos);
        MultiStatusScreenSyncMessage msg = new MultiStatusScreenSyncMessage(makeSyncData(es));
        QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
        for (VisitorMobEntity e : es) {
            e.addStatusListener(new StatusListener() {
                @Override
                public Runnable jobChanged(Function<StatusListener, Runnable> listenToNewJob) {
                    return listenToNewJob.apply(this);
                }

                @Override
                public void statusChanged(IStatus<?> newStatus) {
                    MultiStatusScreen.SyncedData data1 = makeSyncData(es);
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new MultiStatusScreenSyncMessage(data1)
                    );
                }
            });
        }
    }

    private static MultiStatusScreen.@NotNull SyncedData makeSyncData(Iterable<? extends VisitorMobEntity> es) {
        ImmutableMap.Builder<UUID, UtilClean.Pair<JobID, IStatus<?>>> b = ImmutableMap.builder();
        es.forEach(v -> b.put(
                v.getUUID(),
                new UtilClean.Pair<>(v.getJobId(), v.getStatusForServer())
        ));
        ImmutableMap.Builder<UUID, ImmutableList<Item>> b2 = ImmutableMap.builder();
        es.forEach(v -> b2.put(
                v.getUUID(),
                ImmutableList.copyOf(v.getJobJournalSnapshot().items().stream().map(z -> z.get().get()).toList())
        ));
        MultiStatusScreen.SyncedData data1 = new MultiStatusScreen.SyncedData(b.build(), b2.build());
        return data1;
    }

    public void initQuestsMenuClientSide(
            int windowId,
            Collection<UIQuest> quests,
            BlockPos flagPos
    ) {
        questsMenu = new TownQuestsContainer(
                windowId, quests, flagPos, () -> {
        }
        );
    }

    public void initMultiVillagerStatusMenuClientSide(
            int windowId,
            BlockPos flagPos
    ) {
        villagersMenu = new MultiStatusMenu(
                windowId, flagPos, () -> {
        }
        );
    }

    private void initEconClientSide(
            int windowId,
            BlockPos flagPos
    ) {
        econMenu = new TownEconomicsMenu(windowId, flagPos);
    }
}
