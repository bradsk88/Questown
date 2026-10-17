package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Function;

public class FlagTabs extends Tabs implements SubUI {

    public FlagTabs(
            @Nullable Runnable questsScreenFn,
            @Nullable Runnable villagerScreenFn,
            @Nullable Runnable econScreenFn,
            @Nullable Runnable bopScreenFn,
            @Nullable Runnable craftingScreenFn,
            FlagTabsEmbedding.FlagInfo showBOPTab,
            @Nullable String activeTabKey
    ) {
        super(build(questsScreenFn, villagerScreenFn, econScreenFn, bopScreenFn, craftingScreenFn, showBOPTab, activeTabKey));
    }

    private static boolean isSelected(String titleKey, @Nullable String activeTabKey) {
        // The active (open) tab is the one whose title key matches; no tab is highlighted
        // when activeTabKey is null (e.g. the multi-status overview).
        return activeTabKey != null && activeTabKey.equals(titleKey);
    }

    private static @NotNull ImmutableList<Tab> build(
            @Nullable Runnable questsScreenFn,
            @Nullable Runnable villagerScreenFn,
            @Nullable Runnable econScreenFn,
            @Nullable Runnable bopScreenFn,
            @Nullable Runnable craftingScreenFn,
            FlagTabsEmbedding.FlagInfo fi,
            @Nullable String activeTabKey
    ) {
        ImmutableList.Builder<Tab> b = ImmutableList.builder();
        if (fi.showVillagersTab()) {
            b.add(new Tab(
                    (rc, x, y) -> Compat.renderItemAndDecorations(rc.gui(), Items.PLAYER_HEAD.getDefaultInstance(), x + 10, y + 7),
                    setScreen(villagerScreenFn),
                    "tooltips.villagers",
                    isSelected("tooltips.villagers", activeTabKey),
                    fi.villagersTabAttention()
            ));
        }
        if (fi.showQuestsTab()) {
            b.add(new Tab(
                    (rc, x, y) -> Compat.renderItemAndDecorations(rc.gui(), Items.BOOK.getDefaultInstance(), x + 10, y + 7),
                    setScreen(questsScreenFn),
                    "tooltips.quests",
                    isSelected("tooltips.quests", activeTabKey),
                    fi.hasIncompleteQuests()
            ));
        }
        if (fi.showEconTab()) {
            b.add(new Tab(
                    (rc, x, y) -> {
                        int txBefore = RenderSystem.getShaderTexture(0);
                        Util.blitTab(rc.gui(), x, y, 0);
                        RenderSystem.setShaderTexture(0, txBefore);
                    },
                    setScreen(econScreenFn),
                    "tooltips.economics",
                    isSelected("tooltips.economics", activeTabKey),
                    fi.bonusFoodAvailable()
            ));
        }
        if (fi.showBlockOfProgressTab()) {
            b.add(new Tab(
                    (rc, x, y) -> RenderUtil.renderItemScaled(
                            rc.itemRenderer(),
                            2,
                            ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(),
                            x + 10,
                            y + 7
                    ),
                    setScreen(bopScreenFn),
                    "tooltips.blocks_of_progress",
                    isSelected("tooltips.blocks_of_progress", activeTabKey),
                    fi.bopTabAttention()
            ));
        }
        b.add(new Tab(
                (rc, x, y) -> Compat.renderItemAndDecorations(rc.gui(), Items.CRAFTING_TABLE.getDefaultInstance(), x + 10, y + 7),
                setScreen(craftingScreenFn),
                "tooltips.crafting",
                isSelected("tooltips.crafting", activeTabKey),
                false
        ));
        return b.build();
    }

    private static Runnable setScreen(Runnable s) {
        if (s == null) {
            return () -> {
            };
        }
        return s;
    }

    public static Runnable makeOpenFn(
            FlagTabsEmbedding.FlagInfo fp,
            String type
    ) {
        Runnable fn = () -> {
            QT.LOGGER.info("[gui] flag menu request: {}", type);
            QuestownNetwork.sendToServer(new OpenFlagMenuMessage(fp, type));
        };
        return fn;
    }

    public static FlagTabs forMenu(FlagTabsEmbedding menu) {
        return forMenu(menu, null);
    }

    /**
     * Same as {@link #forMenu(FlagTabsEmbedding)} but marks the tab whose tooltip key equals
     * {@code activeTabKey} as selected (highlighted). Each flag screen passes its own tab key so
     * the open tab is visually selected.
     */
    public static FlagTabs forMenu(FlagTabsEmbedding menu, @Nullable String activeTabKey) {
        Collection<String> enabledTabs = menu.getEnabledTabs();

        Function<String, Runnable> factory = typ -> {
            if (enabledTabs.contains(typ)) {
                return makeOpenFn(menu.getFlagInfo(), typ);
            }
            return null;
        };
        return new FlagTabs(
                factory.apply(OpenFlagMenuMessage.QUESTS),
                factory.apply(OpenFlagMenuMessage.VILLAGERS),
                factory.apply(OpenFlagMenuMessage.ECONOMICS),
                factory.apply(OpenFlagMenuMessage.BOP),
                factory.apply(OpenFlagMenuMessage.CRAFTING),
                menu.getFlagInfo(),
                activeTabKey
        );
    }

    public record TabDecision(String titleKey, boolean visible, boolean hasNotification) {}

    public static ImmutableList<TabDecision> computeTabDecisions(FlagTabsEmbedding.FlagInfo fi) {
        ImmutableList.Builder<TabDecision> b = ImmutableList.builder();
        b.add(new TabDecision("tooltips.villagers", fi.showVillagersTab(), fi.villagersTabAttention()));
        b.add(new TabDecision("tooltips.quests", fi.showQuestsTab(), fi.hasIncompleteQuests()));
        b.add(new TabDecision("tooltips.economics", fi.showEconTab(), fi.bonusFoodAvailable()));
        b.add(new TabDecision("tooltips.blocks_of_progress", fi.showBlockOfProgressTab(), fi.bopTabAttention()));
        b.add(new TabDecision("tooltips.crafting", true, false));
        return b.build();
    }

    /**
     * The tab a flag click opens on, or null for the multi-status overview. A full flag opens
     * the BOP tab (its warning); an unseen BOP tab with its red dot outranks the quests tab's dot
     * (playtest 2026-10-17 round 5: "if both have a red dot, the bops tab should get the initial
     * focus").
     */
    public static @Nullable String initialFlagTab(
            boolean bopFull,
            boolean bopTabUnseen,
            boolean questsTabAttention
    ) {
        if (bopFull || bopTabUnseen) {
            return OpenFlagMenuMessage.BOP;
        }
        if (questsTabAttention) {
            return OpenFlagMenuMessage.QUESTS;
        }
        return null;
    }

    public static Collection<String> allExcept(String except) {
        ImmutableList<String> all = ImmutableList.of(
                OpenFlagMenuMessage.VILLAGERS,
                OpenFlagMenuMessage.QUESTS,
                OpenFlagMenuMessage.ECONOMICS,
                OpenFlagMenuMessage.BOP,
                OpenFlagMenuMessage.CRAFTING
        );
        return ImmutableList.copyOf(all.stream().filter(v -> !v.equals(except)).toList());
    }
}
