package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class VillagerTabs extends Tabs implements SubUI {

    public VillagerTabs(
            @Nullable Runnable invScreenFn,
            @Nullable Runnable qScreenFn,
            @Nullable Runnable sScreenFn,
            @Nullable Runnable skillScreenFn,
            @Nullable Runnable changeRootScreenFn,
            @Nullable Runnable econScreenFn,
            @Nullable Runnable bopScreenFn,
            boolean showBopTab,
            boolean showSkillsTab,
            boolean changeRootAttention
    ) {
        super(build(invScreenFn, qScreenFn, sScreenFn, skillScreenFn, changeRootScreenFn, econScreenFn, bopScreenFn, showBopTab, showSkillsTab, changeRootAttention));
    }

    private static @NotNull ImmutableList<Tab> build(
            @Nullable Runnable invScreenFn,
            @Nullable Runnable qScreenFn,
            @Nullable Runnable sScreenFn,
            @Nullable Runnable skillScreenFn,
            @Nullable Runnable changeRootScreenFn,
            @Nullable Runnable econScreenFn,
            @Nullable Runnable bopScreenFn,
            boolean showBopTab,
            boolean showSkillsTab,
            boolean changeRootAttention
    ) {
        ImmutableList.Builder<Tab> b = ImmutableList.builder();
        b.add(new Tab(
                (rc, x, y) -> Compat.renderItemAndDecorations(rc.gui(), Items.CHEST.getDefaultInstance(), x + 10, y + 7),
                setScreen(invScreenFn),
                "tooltips.inventory",
                invScreenFn == null,
                false
        ));
        b.add(new Tab(
                (rc, x, y) -> {
                    int txBefore = RenderSystem.getShaderTexture(0);
                    Util.blitTab(rc.gui(), x, y, 0);
                    RenderSystem.setShaderTexture(0, txBefore);
                }, setScreen(econScreenFn), "tooltips.economics", econScreenFn == null, false
        ));
        b.add(new Tab(
                (rc, x, y) -> {
                    int txBefore = RenderSystem.getShaderTexture(0);
                    RenderSystem.setShaderTexture(0, Questown.ResourceLocation("textures/menu/gatherer/menu.png"));
                    // 1.19.2 golden: blit(stack, x+13, y+11, z=0, u=0, v=0, 9, 9, 256, 256).
                    Util.blit(rc.gui(), Questown.ResourceLocation("textures/menu/gatherer/menu.png"), x + 13, y + 11, 0, 0, 9, 9);
                    RenderSystem.setShaderTexture(0, txBefore);
                }, setScreen(sScreenFn), "tooltips.stats", sScreenFn == null, false
        ));
        if (showSkillsTab) {
            b.add(new Tab(
                    (rc, x, y) -> {
                        int txBefore = RenderSystem.getShaderTexture(0);
                        RenderSystem.setShaderTexture(0, Questown.ResourceLocation("textures/menu/gatherer/menu.png"));
                        PoseStack stack = rc.stack();
                        stack.pushPose();
                        stack.scale(15f / 30f, 15f / 30f, 1);
                        int adjustedX = (int) ((x + 11) / (15f / 30f));
                        int adjustedY = (int) ((y + 7) / (15f / 30f));
                        // 1.19.2 golden: blit(stack, adjX, adjY, z=0, u=17, v=7, 30, 30, 256, 256).
                        Util.blit(rc.gui(), Questown.ResourceLocation("textures/menu/gatherer/menu.png"), adjustedX, adjustedY, 17, 7, 30, 30);
                        stack.popPose();

                        RenderSystem.setShaderTexture(0, txBefore);
                    }, setScreen(skillScreenFn), "tooltips.skills", skillScreenFn == null, false
            ));
        }
        b.add(new Tab(
                (rc, x, y) -> {
                    int txBefore = RenderSystem.getShaderTexture(0);
                    RenderSystem.setShaderColor(0.25f, 0.25f, 0.25f, 1f);
                    Util.blitTab(rc.gui(), x, y, 1);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.setShaderTexture(0, txBefore);
                }, setScreen(changeRootScreenFn), "tooltips.change_root", changeRootScreenFn == null, changeRootAttention
        ));
        b.add(new Tab(
                (rc, x, y) -> Compat.renderItemAndDecorations(rc.gui(), Items.BOOK.getDefaultInstance(), x + 10, y + 7),
                setScreen(qScreenFn),
                "tooltips.quests",
                qScreenFn == null,
                false
        ));
        if (showBopTab) {
            b.add(new Tab(
                    (rc, x, y) -> RenderUtil.renderItemScaled(
                            rc.itemRenderer(),
                            2,
                            ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(),
                            x + 10,
                            y + 7
                    ), setScreen(bopScreenFn), "tooltips.blocks_of_progress", bopScreenFn == null, true
            ));
        }
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
            BlockPos fp,
            UUID gathererId,
            String type,
            boolean showInventoryTab
    ) {
        Runnable fn = () -> QuestownNetwork.sendToServer(new OpenVillagerMenuMessage(
                fp.getX(),
                fp.getY(),
                fp.getZ(),
                gathererId,
                type,
                showInventoryTab
        ));
        return fn;
    }

    public static VillagerTabs forMenu(VillagerTabsEmbedding menu) {
        Collection<String> enabledTabs = menu.getEnabledTabs();

        Function<String, Runnable> factory = typ -> {
            if (enabledTabs.contains(typ)) {
                return makeOpenFn(menu.getFlagPos(), menu.getVillagerUUID(), typ, menu.showInventoryTab());
            }
            return null;
        };
        // The skills-tab gate rides on the menu, not the tab list: pre-level (no unlockable
        // jobs yet) the server keeps SKILLS out of enabledTabs via hideSkills, and forMenu
        // simply disables it for every tabbed villager menu.
        Runnable skillFn = menu.showSkillsTab() ? factory.apply(OpenVillagerMenuMessage.SKILLS) : null;
        return new VillagerTabs(
                factory.apply(OpenVillagerMenuMessage.INVENTORY),
                factory.apply(OpenVillagerMenuMessage.QUESTS),
                factory.apply(OpenVillagerMenuMessage.STATS),
                skillFn,
                factory.apply(OpenVillagerMenuMessage.CHANGE_ROOT),
                factory.apply(OpenVillagerMenuMessage.ECONOMICS),
                factory.apply(OpenVillagerMenuMessage.BOP),
                menu.showBlockOfProgressTab(),
                menu.showSkillsTab(),
                CHANGE_ROOT_ATTENTION.getOrDefault(menu.getVillagerUUID(), false)
        );
    }

    /**
     * Client-side: the change-profession red dot per villager, from the last villager-menu
     * payload (server-computed, TownQuests.wantsJobChangeFrom). Every tab click re-opens the
     * menu, so the entry refreshes on each open; only the client renders tabs, so the flag
     * rides the payload instead of each of the five menu constructors.
     */
    private static final Map<UUID, Boolean> CHANGE_ROOT_ATTENTION = new HashMap<>();

    static void recordChangeRootAttention(UUID villager, boolean attention) {
        CHANGE_ROOT_ATTENTION.put(villager, attention);
    }

    /** The tab list minus the skills tab (the level gate: no unlockable jobs, no tab). */
    public static Collection<String> hideSkills(Collection<String> tabs) {
        return tabs.stream().filter(v -> !v.equals(OpenVillagerMenuMessage.SKILLS)).toList();
    }

    /** The tab list minus the inventory tab (management-only flag click-through). */
    public static Collection<String> hideInventory(Collection<String> tabs) {
        return tabs.stream().filter(v -> !v.equals(OpenVillagerMenuMessage.INVENTORY)).toList();
    }

    public static Collection<String> except(String except) {
        ImmutableList<String> all = ImmutableList.of(
                OpenVillagerMenuMessage.INVENTORY,
                OpenVillagerMenuMessage.BOP,
                OpenVillagerMenuMessage.QUESTS,
                OpenVillagerMenuMessage.STATS,
                OpenVillagerMenuMessage.SKILLS,
                OpenVillagerMenuMessage.CHANGE_ROOT,
                OpenVillagerMenuMessage.ECONOMICS
        );
        return ImmutableList.copyOf(all.stream().filter(v -> !v.equals(except)).toList());
    }

    /**
     * The tab a villager menu opens on when the player opens it (not a tab click): the change
     * profession tab while it carries its red dot (playtest 2026-10-17 round 5), else the
     * inventory, or the stats pane for the management view that has no inventory tab.
     */
    public static String initialTab(boolean changeRootAttention, boolean showInventoryTab) {
        if (changeRootAttention) {
            return OpenVillagerMenuMessage.CHANGE_ROOT;
        }
        if (showInventoryTab) {
            return OpenVillagerMenuMessage.INVENTORY;
        }
        return OpenVillagerMenuMessage.STATS;
    }

    public static Collection<String> all() {
        return except("");
    }
}
