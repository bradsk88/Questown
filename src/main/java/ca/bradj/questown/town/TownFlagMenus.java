package ca.bradj.questown.town;

import ca.bradj.questown.QT;
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
import java.util.function.Supplier;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCQuest;
import ca.bradj.questown.town.quests.MCReward;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.roomrecipes.recipes.RecipesInit;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import ca.bradj.questown.mc.RecipeRef;
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
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TownFlagMenus {


    public void showUI(
            ServerPlayer sender,
            String type,
            FlagTabsEmbedding.FlagInfo inputFlagInfo,
            int blocksOfProgress
    ) {
        BlockPos flagPos = inputFlagInfo.flagPos();
        TownFlagBlockEntity flagEntity = (TownFlagBlockEntity) sender.serverLevel().getBlockEntity(flagPos);
        TownInterface flag = flagEntity;
        if (OpenFlagMenuMessage.VILLAGERS.equals(type)) {
            // Clear the villagers-tab red dot before building the info the screen renders.
            flagEntity.markVillagersTabSeen();
        }
        FlagTabsEmbedding.FlagInfo realFlagInfo = flagEntity.getInfo();

        @SuppressWarnings("DataFlowIssue")
        ImmutableList<AbstractMap.SimpleEntry<MCQuest, MCReward>> quests = flag.getQuestHandle()
                                                                               .getAllQuestsWithRewards();
        List<UIQuest> uiQuests = UIQuest.fromLevel(
                sender.serverLevel(),
                quests,
                flagEntity.getQuestHandle().openQuestProgress()
        );

        QuestScreenSummary summary = new QuestScreenSummary(
                QuestCompletion.allComplete(uiQuests.stream().map(q -> q.status).toList()),
                flagEntity.hasPendingMorningReward()
        );

        @SuppressWarnings("rawtypes") Collection entities = flag.getVillagerHandle().entities();

        @SuppressWarnings("unchecked") ImmutableMap<String, Runnable> showers = ImmutableMap.of(
                OpenFlagMenuMessage.QUESTS,
                () -> {
                    triggerAdvancementForAnyFarms(sender, UtilClean.keys(quests));
                    openMenu(
                            sender, (windowId, inv, p) -> new TownQuestsContainer(
                                    windowId, uiQuests, realFlagInfo, summary,
                                    () -> triggerAdvancement(flagPos, sender.serverLevel())
                            ), uiQuests, realFlagInfo, entities, flagEntity.depositedBopRootJobs(),
                            () -> flagEntity.hasVillagerArrivingInMorning(), summary
                    );
                },
                OpenFlagMenuMessage.VILLAGERS,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new MultiStatusMenu(
                                windowId, realFlagInfo, () -> triggerAdvancement(flagPos, sender.serverLevel())
                        ), uiQuests, realFlagInfo, entities, flagEntity.depositedBopRootJobs(),
                        () -> flagEntity.hasVillagerArrivingInMorning(), summary
                ),
                OpenFlagMenuMessage.ECONOMICS,
                () -> {
                    QuestownNetwork.sendToPlayer(sender, new EconomicsUpdate(flag.getEconomicsHandle().getAggregatedItems(null)));
                    openMenu(
                            sender, (windowId, inv, p) -> new TownEconomicsMenu(
                                    windowId, realFlagInfo
                            ), uiQuests, realFlagInfo, entities, flagEntity.depositedBopRootJobs(),
                            () -> flagEntity.hasVillagerArrivingInMorning(), summary
                    );
                },
                OpenFlagMenuMessage.BOP,
                () -> {
                    openMenu(
                            sender, (windowId, inv, p) -> new TownBlockofProgressMenu(
                                    windowId, realFlagInfo, flagEntity.depositedBopRootJobs()
                            ), uiQuests, realFlagInfo, entities, flagEntity.depositedBopRootJobs(),
                            () -> flagEntity.hasVillagerArrivingInMorning(), summary
                    );
                    AdvancementsInit.TUTORIAL_TRIGGER.trigger(sender, TutorialTrigger.Triggers.FirstBopView);
                },
                OpenFlagMenuMessage.CRAFTING,
                () -> openMenu(
                        sender, (windowId, inv, p) -> new FlagCraftingMenu(
                                windowId, realFlagInfo
                        ), uiQuests, realFlagInfo, entities, flagEntity.depositedBopRootJobs(),
                        () -> flagEntity.hasVillagerArrivingInMorning(), summary
                )
        );

        Runnable runnable = showers.get(type);
        if (runnable == null) {
            // The type comes from a client packet: reject an unknown one instead of throwing in the
            // network handler (2026-10-21: a dev-tool "bop" for "blocks_of_progress").
            QT.GUI_LOGGER.error("Unexpected menu type: \"{}\"", type);
            return;
        }
        QT.GUI_LOGGER.info("Opening flag menu: {}", type);
        runnable.run();
    }


    private void triggerAdvancementForAnyFarms(
            ServerPlayer p,
            Collection<? extends Quest<ResourceLocation, ?>> q
    ) {
        // The holder owns the id in 1.20.2+; the codec-constructed RoomRecipe carries a null id.
        // Eligibility = (holder-id match) AND (value is a farm recipe) - both, in that order.
        for (Quest<ResourceLocation, ?> mcQuest : q) {
            ResourceLocation wanted = mcQuest.getWantedId();
            RecipeRef.RecipePair<RoomRecipe> pair = wanted == null
                    ? null
                    : RecipeRef.findById(p.level().getRecipeManager(), RecipesInit.ROOM, wanted);
            if (pair == null || !pair.value().isFarmRecipe()) {
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
            com.google.common.collect.ImmutableList<String> bopRootJobs,
            Supplier<Boolean> morningSpawnPending,
            QuestScreenSummary summary
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
                }, data -> {
                    Map<UUID, String> displayNames = Map.of();
                    if (sender.level().getBlockEntity(flagPos.flagPos())
                            instanceof TownFlagBlockEntity flag) {
                        displayNames = flag.getVillagerHandle().displayNamesForAll();
                    }
                    FlagMenus.writeAndLink(
                            data, quests, flagPos, sender, entities, bopRootJobs, morningSpawnPending,
                            summary, displayNames
                    );
                }
        );
    }

}
