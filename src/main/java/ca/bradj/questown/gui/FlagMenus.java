package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.MultiStatusScreenSyncMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.StatusListener;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class FlagMenus {
    TownQuestsContainer questsMenu;
    MultiStatusMenu villagersMenu;

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
            menus.initQuestsMenu(windowId, quests, flagPos);
            menus.initMultiVillagerStatusMenu(windowId, flagPos);
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
        ImmutableMap.Builder<UUID, UtilClean.Pair<JobID, IStatus<?>>> b = ImmutableMap.builder();
        es.forEach(v -> b.put(
                v.getUUID(),
                new UtilClean.Pair<>(v.getJobId(), v.getStatusForServer())
        ));
        QuestownNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MultiStatusScreenSyncMessage(
                new MultiStatusScreen.SyncedData(b.build())
        ));
        for (VisitorMobEntity e : es) {
            e.addStatusListener(new StatusListener() {
                @Override
                public Runnable jobChanged(Function<StatusListener, Runnable> listenToNewJob) {
                    return listenToNewJob.apply(this);
                }

                @Override
                public void statusChanged(IStatus<?> newStatus) {
                    ImmutableMap.Builder<UUID, UtilClean.Pair<JobID, IStatus<?>>> b = ImmutableMap.builder();
                    es.forEach(v -> b.put(
                            v.getUUID(),
                            new UtilClean.Pair<>(v.getJobId(), v.getStatusForServer())
                    ));
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new MultiStatusScreenSyncMessage(new MultiStatusScreen.SyncedData(b.build()))
                    );
                }
            });
        }
    }

    public void initQuestsMenu(
            int windowId,
            Collection<UIQuest> quests,
            BlockPos flagPos
    ) {
        questsMenu = new TownQuestsContainer(windowId, quests, flagPos);
    }

    public void initMultiVillagerStatusMenu(
            int windowId,
            BlockPos flagPos
    ) {
        villagersMenu = new MultiStatusMenu(windowId, flagPos);
    }
}
