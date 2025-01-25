package ca.bradj.questown.town;

import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class TownFlagMenus {


    public void showUI(
            ServerPlayer sender,
            String type,
            BlockPos flagPos
    ) {
        // TODO: Make it possible to change villager jobs from the flag?
//        syncWorkToClient(sender);

        TownInterface flag = (TownFlagBlockEntity) sender.getLevel().getBlockEntity(flagPos);

        @SuppressWarnings("DataFlowIssue") List<UIQuest> quests = UIQuest.fromLevel(
                sender.getLevel(),
                flag.getQuestHandle().getAllQuestsWithRewards()
        );

        @SuppressWarnings("rawtypes") Collection entities = flag.getVillagerHandle().entities();

        @SuppressWarnings("unchecked") ImmutableMap<String, Runnable> showers = ImmutableMap.of(
                OpenFlagMenuMessage.QUESTS,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new TownQuestsContainer(
                                windowId, quests, flagPos, () -> triggerAdvancement(flagPos, sender.getLevel())
                        ), quests, flagPos, entities
                ),
                OpenFlagMenuMessage.VILLAGERS,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new MultiStatusMenu(
                                windowId, flagPos, () -> triggerAdvancement(flagPos, sender.getLevel())
                        ), quests, flagPos, entities
                ),
                OpenFlagMenuMessage.ECONOMICS,
                () -> {
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new EconomicsUpdate(flag.getEconomicsHandle().getAggregatedItems(null))
                    );
                    openMenu(
                            sender, (windowId, inv, p) -> new TownEconomicsMenu(
                                    windowId, flagPos
                            ), quests, flagPos, entities
                    );
                }
        );

        Runnable runnable = showers.get(type);
        if (runnable == null) {
            throw new IllegalArgumentException("Unexpected menu type: \"" + type + "\"");
        }
        runnable.run();
    }

    private void triggerAdvancement(
            BlockPos pos,
            ServerLevel level
    ) {
        AdvancementsInit.ROOM_TRIGGER.triggerForNearestPlayer(level, RoomTrigger.Triggers.FirstOpenFlagMenu, pos);
    }

    private static void openMenu(
            ServerPlayer sender,
            TriFunction<Integer, Inventory, Player, AbstractContainerMenu> shower,
            List<UIQuest> quests,
            BlockPos flagPos,
            Iterable<? extends VisitorMobEntity> entities
    ) {
        Compat.openScreen(
                sender, new MenuProvider() {
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
                        return shower.apply(windowId, inv, p);
                    }
                }, data -> FlagMenus.writeAndLink(data, quests, flagPos, sender, entities)
        );
    }

}
