package ca.bradj.questown.town;

import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
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
            FlagTabsEmbedding.FlagInfo flagInfo,
            int blocksOfProgress
    ) {
        // TODO: Make it possible to change villager jobs from the flag?
//        syncWorkToClient(sender);

        BlockPos flagPos = flagInfo.flagPos();
        TownInterface flag = (TownFlagBlockEntity) sender.getLevel().getBlockEntity(flagPos);

        @SuppressWarnings("DataFlowIssue") List<UIQuest> quests = UIQuest.fromLevel(
                sender.getLevel(),
                flag.getQuestHandle().getAllQuestsWithRewards()
        );

        @SuppressWarnings("rawtypes") Collection entities = flag.getVillagerHandle().entities();

        @SuppressWarnings("unchecked") ImmutableMap<String, Runnable> showers = ImmutableMap.of(
                OpenFlagMenuMessage.QUESTS,
                () -> {
                    triggerFarmAdvancement(sender, quests);
                    openMenu(
                            sender, (windowId, inv, p) -> new TownQuestsContainer(
                                    windowId, quests, flagInfo, () -> triggerAdvancement(flagPos, sender.getLevel())
                            ), quests, flagInfo, entities, flag.getBlocksOfProgress()
                    );
                },
                OpenFlagMenuMessage.VILLAGERS,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new MultiStatusMenu(
                                windowId, flagInfo, () -> triggerAdvancement(flagPos, sender.getLevel())
                        ), quests, flagInfo, entities, flag.getBlocksOfProgress()
                ),
                OpenFlagMenuMessage.ECONOMICS,
                () -> {
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new EconomicsUpdate(flag.getEconomicsHandle().getAggregatedItems(null))
                    );
                    openMenu(
                            sender, (windowId, inv, p) -> new TownEconomicsMenu(
                                    windowId, flagInfo
                            ), quests, flagInfo, entities, flag.getBlocksOfProgress()
                    );
                },
                OpenFlagMenuMessage.BOP,
                () -> {
                    openMenu(
                            sender, (windowId, inv, p) -> new TownBlockofProgressMenu(
                                    windowId, flagInfo, blocksOfProgress
                            ), quests, flagInfo, entities, flag.getBlocksOfProgress()
                    );
                }
        );

        Runnable runnable = showers.get(type);
        if (runnable == null) {
            throw new IllegalArgumentException("Unexpected menu type: \"" + type + "\"");
        }
        runnable.run();
    }


    private void triggerFarmAdvancement(
            ServerPlayer p,
            List<UIQuest> q
    ) {
        List<RoomRecipe> farmRecipes = p.level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).stream()
                                              .filter(RoomRecipe::isFarmRecipe).toList();
        for (UIQuest mcQuest : q) {
            if (farmRecipes.stream().noneMatch(z -> z.getId().equals(mcQuest.getRecipeId()))) {
                continue;
            }
            AdvancementsInit.VISITOR_TRIGGER.trigger(p, VisitorTrigger.Triggers.FirstFarmQuest);
            break;
        }
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
            FlagTabsEmbedding.FlagInfo flagPos,
            Iterable<? extends VisitorMobEntity> entities,
            int bopCount
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
                }, data -> FlagMenus.writeAndLink(data, quests, flagPos, sender, entities, bopCount)
        );
    }

}
