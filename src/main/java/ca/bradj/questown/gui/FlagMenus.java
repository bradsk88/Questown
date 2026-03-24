package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.town.status.MultiStatusScreen;
import ca.bradj.questown.gui.town.status.MultiStatusScreenSyncMessage;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.StatusListener;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class FlagMenus {
    TownQuestsContainer questsMenu;
    MultiStatusMenu villagersMenu;
    TownEconomicsMenu econMenu;
    TownBlockofProgressMenu bopMenu;
    FlagCraftingMenu craftingMenu;

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
            TownBlockofProgressMenu.ReadResult bopResult = TownBlockofProgressMenu.readWithFlagInfo(buf);
            int blocksOfProgress = bopResult.blocksOfProgress();
            FlagTabsEmbedding.FlagInfo flagInfo = bopResult.flagInfo();

            FlagMenus menus = new FlagMenus();
            // Never provide these initializers with the entity, itself. Instead, pass the entity's UUID.
            // It tends to cause client-side-only bugs that don't show up in the dev environment.
            menus.initQuestsMenuClientSide(windowId, quests, flagInfo);
            menus.initMultiVillagerStatusMenuClientSide(windowId, flagInfo);
            menus.initEconClientSide(windowId, flagInfo);
            menus.initBlocksOfProgress(windowId, flagInfo, blocksOfProgress);
            menus.initCrafting(windowId, flagInfo);
            return menus;
        } catch (Exception e) {
            QT.GUI_LOGGER.error("Failed to open town quests container: {}", e.getMessage());
            throw e;
        }
    }

    public static void writeAndLink(
            FriendlyByteBuf buf,
            List<UIQuest> quests,
            FlagTabsEmbedding.FlagInfo flagInfo,
            ServerPlayer player,
            Iterable<? extends VisitorMobEntity> es,
            int bopCount
    ) {
        TownQuestsContainer.write(buf, quests, flagInfo.flagPos());
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
        TownBlockofProgressMenu.write(buf, flagInfo, bopCount);
    }

    private static MultiStatusScreen.@NotNull SyncedData makeSyncData(Iterable<? extends VisitorMobEntity> es) {
        HashMap<UUID, StatusPacket> b = new HashMap<>();
        HashMap<UUID, ImmutableList<Item>> b2 = new HashMap<>();
        for (VisitorMobEntity v : es) {
            if (b.containsKey(v.getUUID()) || b2.containsKey(v.getUUID())) {
                QT.FLAG_LOGGER.error("Villager {} detected twice. This is probably a bug!", v.getUUID());
            }
            IStatus<?> vStatus = v.getStatusForServer();
            StatusPacket value = createStatusPacket(v, vStatus);
            b.put(v.getUUID(), value);
            List<Item> list = v.getJobJournalSnapshot()
                               .items()
                               .stream()
                               .map(z -> z.get().get())
                               .toList();
            b2.put(v.getUUID(), ImmutableList.copyOf(list));
        }
        MultiStatusScreen.SyncedData data1 = new MultiStatusScreen.SyncedData(b, b2);
        return data1;
    }

    private static @NotNull StatusPacket createStatusPacket(
            VisitorMobEntity v,
            IStatus<?> vStatus
    ) {
        ResourceLocation tex = ServerJobsRegistry.getTexture(v.getJobId(), vStatus);
        ImmutableList<Component> texts = ServerJobsRegistry.getStatusText(v.getJobId(), vStatus);
        StatusPacket value = new StatusPacket(v.getJobId(), texts, tex);
        return value;
    }

    public void initQuestsMenuClientSide(
            int windowId,
            Collection<UIQuest> quests,
            FlagTabsEmbedding.FlagInfo flag
    ) {
        questsMenu = new TownQuestsContainer(
                windowId, quests, flag, () -> {
        }
        );
    }

    public void initMultiVillagerStatusMenuClientSide(
            int windowId,
            FlagTabsEmbedding.FlagInfo flag
    ) {
        villagersMenu = new MultiStatusMenu(
                windowId, flag, () -> {
        }
        );
    }

    private void initEconClientSide(
            int windowId,
            FlagTabsEmbedding.FlagInfo flag
    ) {
        econMenu = new TownEconomicsMenu(windowId, flag);
    }

    private void initBlocksOfProgress(
            int windowId,
            FlagTabsEmbedding.FlagInfo flagPos,
            int blocksOfProgress
    ) {
        bopMenu = new TownBlockofProgressMenu(windowId, flagPos, blocksOfProgress);
    }

    private void initCrafting(
            int windowId,
            FlagTabsEmbedding.FlagInfo flagInfo
    ) {
        craftingMenu = new FlagCraftingMenu(windowId, flagInfo);
    }
}
