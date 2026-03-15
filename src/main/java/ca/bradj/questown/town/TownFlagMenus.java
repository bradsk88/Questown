package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.advancements.RoomTrigger;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.advancements.VisitorTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.network.EconomicsUpdate;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.PacketDistributor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;

public class TownFlagMenus {


    public void showUI(
            ServerPlayer sender,
            String type,
            FlagTabsEmbedding.FlagInfo inputFlagInfo,
            int blocksOfProgress
    ) {
        BlockPos flagPos = inputFlagInfo.flagPos();
        TownFlagBlockEntity flagEntity = (TownFlagBlockEntity) sender.getLevel().getBlockEntity(flagPos);
        TownInterface flag = flagEntity;
        FlagTabsEmbedding.FlagInfo realFlagInfo = flagEntity.getInfo();

        @SuppressWarnings("DataFlowIssue")
        ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> quests = flag.getQuestHandle()
                                                                               .getAllQuestsWithRewards();
        List<UIQuest> uiQuests = UIQuest.fromLevel(sender.getLevel(), quests);

        @SuppressWarnings("rawtypes") Collection entities = flag.getVillagerHandle().entities();

        @SuppressWarnings("unchecked") ImmutableMap<String, Runnable> showers = ImmutableMap.of(
                OpenFlagMenuMessage.QUESTS,
                () -> {
                    triggerAdvancementForAnyFarms(sender, UtilClean.keys(quests));
                    openMenu(
                            sender, (windowId, inv, p) -> new TownQuestsContainer(
                                    windowId, uiQuests, realFlagInfo, () -> triggerAdvancement(flagPos, sender.getLevel())
                            ), uiQuests, realFlagInfo, entities, flag.getBlocksOfProgress()
                    );
                },
                OpenFlagMenuMessage.VILLAGERS,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new MultiStatusMenu(
                                windowId, realFlagInfo, () -> triggerAdvancement(flagPos, sender.getLevel())
                        ), uiQuests, realFlagInfo, entities, flag.getBlocksOfProgress()
                ),
                OpenFlagMenuMessage.ECONOMICS,
                () -> {
                    QuestownNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> sender),
                            new EconomicsUpdate(flag.getEconomicsHandle().getAggregatedItems(null))
                    );
                    openMenu(
                            sender, (windowId, inv, p) -> new TownEconomicsMenu(
                                    windowId, realFlagInfo
                            ), uiQuests, realFlagInfo, entities, flag.getBlocksOfProgress()
                    );
                },
                OpenFlagMenuMessage.BOP,
                () -> {
                    openMenu(
                            sender, (windowId, inv, p) -> new TownBlockofProgressMenu(
                                    windowId, realFlagInfo, blocksOfProgress
                            ), uiQuests, realFlagInfo, entities, flag.getBlocksOfProgress()
                    );
                    AdvancementsInit.TUTORIAL_TRIGGER.trigger(sender, TutorialTrigger.Triggers.FirstBopView);
                },
                OpenFlagMenuMessage.CRAFTING,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new FlagCraftingMenu(
                                windowId, realFlagInfo
                        ), uiQuests, realFlagInfo, entities, flag.getBlocksOfProgress()
                )
        );

        Runnable runnable = showers.get(type);
        if (runnable == null) {
            throw new IllegalArgumentException("Unexpected menu type: \"" + type + "\"");
        }
        runnable.run();
    }


    private void triggerAdvancementForAnyFarms(
            ServerPlayer p,
            Collection<? extends Quest<ResourceLocation, ?>> q
    ) {
        List<RoomRecipe> farmRecipes = p.level.getRecipeManager().getAllRecipesFor(RecipesInit.ROOM).stream()
                                              .filter(RoomRecipe::isFarmRecipe).toList();
        for (Quest<?, ?> mcQuest : q) {
            if (farmRecipes.stream().noneMatch(z -> z.getId().equals(mcQuest.getWantedId()))) {
                continue;
            }
            AdvancementsInit.VISITOR_TRIGGER.trigger(p, VisitorTrigger.Triggers.FirstFarmQuest);
            return;
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
