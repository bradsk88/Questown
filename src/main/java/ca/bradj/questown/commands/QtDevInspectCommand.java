package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.mobs.helperchicken.ChickenArcConditions;
import ca.bradj.questown.mobs.helperchicken.ChickenArcTransitions;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlags;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Quest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Dev-only: a read-only stand-in for a player right-clicking something, so an
 * agent driving a held run can "look closer" at a flag or the helper chicken the
 * way a brand-new player would, without a cheatsheet.
 *
 * <p>It returns the same static view a player gets from the menu, nothing behind
 * it: for a flag, the menu tabs (villagers, quests, economics, block of
 * progress, crafting) and their static content; for the chicken, the current
 * beat narrated as the chicken's observed behavior, dynamically from the
 * player's held item. It never prints the internal {@link ChickenBeatState}
 * enum, a {@code status}/condition line, or the arc map, and it changes no
 * state. The action (placing a block, right-clicking) stays a separate command.
 *
 * <p>No permission gate, so it runs from the console (FIFO). Registered under
 * {@code _qtdev inspect}, replacing the direct {@code qt quests} /
 * {@code _qtdev villagers} commands (the cheat version that reached the quest
 * board without going through the menu the chicken opens).
 */
public class QtDevInspectCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> src,
            CommandBuildContext ctx
    ) {
        var inspect = Commands.literal("inspect");
        inspect.then(posArg().executes(css -> runMenu(css.getSource(),
                BlockPosArgument.getLoadedBlockPos(css, "pos"))));
        inspect.then(Commands.literal("villagers")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "villagers"))));
        inspect.then(Commands.literal("quests")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "quests"))));
        inspect.then(Commands.literal("economy")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "economy"))));
        inspect.then(Commands.literal("blocks")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "blocks"))));
        inspect.then(Commands.literal("crafting")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "crafting"))));
        inspect.then(Commands.literal("chicken")
                .then(posArg().executes(css -> runChicken(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos")))));
        src.register(Commands.literal("_qtdev").then(inspect));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.coordinates.Coordinates> posArg() {
        return Commands.argument("pos", BlockPosArgument.blockPos());
    }

    /** The flag menu: which tabs are shown, with red-dots, and the static content. */
    private static int runMenu(CommandSourceStack source, BlockPos pos) {
        TownFlagBlockEntity flag = flagAt(pos);
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[_qtdev inspect] no town flag at {}", pos);
            return 1;
        }
        FlagTabsEmbedding.FlagInfo info = flag.getInfo();
        QT.FLAG_LOGGER.info("[_qtdev inspect] flag {} - the menu a player would see:",
                pos.toShortString());
        listTabs(info, flag);
        return 0;
    }

    /** One tab's static content, mirroring what the menu's payload carries. */
    private static int runTab(
            CommandSourceStack source, BlockPos pos, String tab
    ) {
        TownFlagBlockEntity flag = flagAt(pos);
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[_qtdev inspect] no town flag at {}", pos);
            return 1;
        }
        if (!tabShown(flag.getInfo(), tab)) {
            QT.FLAG_LOGGER.info("[_qtdev inspect] the {} tab is not shown for flag {}",
                    tab, pos.toShortString());
            return 0;
        }
        switch (tab) {
            case "villagers" -> printVillagers(flag);
            case "quests" -> printQuests(flag);
            case "economy" -> printEconomy(flag);
            case "blocks" -> printBlocks(flag);
            case "crafting" -> printCrafting(flag);
            default -> QT.FLAG_LOGGER.warn("[_qtdev inspect] unknown tab {}", tab);
        }
        return 0;
    }

    /** The chicken's current beat, narrated as the chicken's observed behavior. */
    private static int runChicken(CommandSourceStack source, BlockPos pos) {
        TownFlagBlockEntity flag = flagAt(pos);
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[_qtdev inspect] no town flag at {}", pos);
            return 1;
        }
        ChickenBeatState state = flag.getChickenBeatState();
        if (state == ChickenBeatState.COMPLETE) {
            QT.FLAG_LOGGER.info("[_qtdev inspect] the chicken has finished guiding you; the flag is "
                    + "onboarding complete.");
            return 0;
        }
        if (state == ChickenBeatState.FORFEIT) {
            QT.FLAG_LOGGER.info("[_qtdev inspect] the chicken has stopped guiding you.");
            return 0;
        }
        QT.FLAG_LOGGER.info("[_qtdev inspect] {}", chickenBehavior(flag, state));
        return 0;
    }

    /**
     * The chicken's behavior, narrated as a player sees it and keyed off the
     * player's held item so the same beat reads differently in different states
     * (a new player sees the chicken's behavior change when they hold the right
     * item). The internal state name, a condition line, and the arc map are never
     * printed - only what the player would see.
     */
    private static String chickenBehavior(TownFlagBlockEntity flag, ChickenBeatState state) {
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        boolean holdsWand = ChickenArcConditions.hasWandInInventory(player);
        return switch (state) {
            case WAITING_FOR_STICK -> holdsWand
                    ? "The chicken looks at your town wand, then back to the flag base. "
                            + "[Use the town wand on the flag base]"
                    : "The chicken looks at where you are holding your hand, then back to the "
                            + "flag base. [Get the town wand - right-click the flag with a stick]";
            case WAITING_FOR_WAND_ON_CAMPFIRE -> holdsWand
                    ? "The chicken looks at the campfire near the flag. [Light a campfire near "
                            + "the flag]"
                    : "The chicken looks at the campfire near the flag, then at your empty hand. "
                            + "[Light a campfire near the flag, then hold the town wand]";
            case SUNSET_AND_MAP -> "The chicken looks at the dark sky, then at the campfire. "
                    + "[Come back at night and sleep on the campfire with the wand]";
            case WAITING_FOR_WALL_BLOCK -> "The chicken pecks at a gap in the wall. [Put a solid "
                    + "block in the gap the chicken pecks at]";
            case WAITING_FOR_DOOR -> "The chicken pecks at a gap in the wall. [Put a door in the "
                    + "gap the chicken pecks at]";
            case WAITING_FOR_WAND_ON_DOOR -> holdsWand
                    ? "The chicken looks at the door, then at your town wand. [Use the town wand "
                            + "on the door]"
                    : "The chicken looks at the door, then at your empty hand. [Hold the town wand "
                            + "and use it on the door]";
            case WAITING_FOR_SIGN -> "The chicken looks into the room and pecks. [Put a sign in "
                    + "the room]";
            case WAITING_FOR_CHEST -> "The chicken looks into the room and pecks. [Put a chest in "
                    + "the room]";
            case WAITING_FOR_PRESSURE_PLATE -> "The chicken looks at the gate, then at your hand. "
                    + "[Make a welcome mat and put it on the gate]";
            case WAITING_FOR_FREE_FOOD -> "The chicken looks at your hand, then at the flag. "
                    + "[Right-click the flag with a block of dirt - optional]";
            case WAITING_FOR_VILLAGER_UI -> "The chicken looks at a villager in the town. [Look at "
                    + "a villager]";
            case WAITING_FOR_FLAG_UI -> "The chicken looks at the flag. [Look at the flag]";
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> "The chicken looks at the Worldly Seeds, then "
                    + "at you. [Give the Worldly Seeds to the chicken]";
            case COMPLETE, FORFEIT -> "";
        };
    }

    // --- the menu tabs ---

    private static void listTabs(FlagTabsEmbedding.FlagInfo info, TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect]   villagers {}{}",
                info.showVillagersTab() ? "shown" : "hidden", "");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   quests {}{}",
                info.showQuestsTab() ? "shown" : "hidden",
                info.hasIncompleteQuests() ? " (red-dot)" : "");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   economics {}",
                info.showEconTab() ? "shown" : "hidden");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   block of progress {}{}",
                info.showBlockOfProgressTab() ? "shown" : "hidden",
                info.bopFull() ? " (red-dot)" : "");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   crafting shown");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   block-of-progress count: {}",
                flag.getBlocksOfProgress());
        if (!flag.getVillagerHandle().entities().isEmpty()) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   villagers in town: {}",
                    flag.getVillagerHandle().entities().size());
        }
        if (flag.hasVillagerArrivingInMorning()) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   villagers will arrive in the morning");
        }
    }

    private static void printVillagers(TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect] villagers tab:");
        boolean any = false;
        for (net.minecraft.world.entity.LivingEntity e : flag.getVillagerHandle().entities()) {
            if (!(e instanceof VisitorMobEntity villager)) {
                continue;
            }
            any = true;
            TownieNeed need = villager.getNeed();
            QT.FLAG_LOGGER.info("[_qtdev inspect]   villager {} job={} need={}",
                    villager.getUUID(), villager.getJobId(), need);
            if (need == TownieNeed.UNMET_ITEM) {
                String name = resolveItemName(villager.getUnmetItem());
                QT.FLAG_LOGGER.info("[_qtdev inspect]     needs item: {}{}",
                        name != null ? name : villager.getUnmetItem(),
                        name != null ? " (" + villager.getUnmetItem() + ")" : "");
            }
        }
        if (!any) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   no villagers");
        }
    }

    private static void printQuests(TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect] quests tab:");
        boolean any = false;
        for (UIQuest q : UIQuest.fromLevel(
                flag.getServerLevel(),
                flag.getQuestHandle().getAllQuestsWithRewards())) {
            any = true;
            QT.FLAG_LOGGER.info("[_qtdev inspect]   {} wants {} ({} x{})",
                    q.status, q.getWantedId(), explain(q), q.getIngredients().size());
        }
        if (!any) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   no quests");
        }
    }

    private static void printEconomy(TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect] economics tab:");
        var items = flag.getEconomicsHandle().getAggregatedItems(null);
        if (items.isEmpty()) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   no items in the economy");
        }
        for (var item : items) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   {}", item);
        }
    }

    private static void printBlocks(TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect] block-of-progress tab:");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   blocks of progress: {}{}",
                flag.getBlocksOfProgress(), flag.isBopFull() ? " (full - red-dot)" : "");
    }

    private static void printCrafting(TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect] crafting tab:");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   the crafting tab shows the flag's craftable "
                + "recipes (no per-tab payload beyond the flag)");
    }

    // --- helpers ---

    private static boolean tabShown(FlagTabsEmbedding.FlagInfo info, String tab) {
        return switch (tab) {
            case "villagers" -> info.showVillagersTab();
            case "quests" -> info.showQuestsTab();
            case "economy" -> info.showEconTab();
            case "blocks" -> info.showBlockOfProgressTab();
            case "crafting" -> true;
            default -> false;
        };
    }

    private static String explain(UIQuest q) {
        Quest.QuestType type = q.getType();
        if (type == Quest.QuestType.ITEM) {
            return q.getIngredients().isEmpty()
                    ? "item " + q.getWantedId()
                    : q.getIngredients().iterator().next().toString();
        }
        if (type == Quest.QuestType.ROOM) {
            return "room recipe " + q.getWantedId();
        }
        if (type == Quest.QuestType.JOB_CHANGE) {
            return "job: " + q.getWantedId();
        }
        return String.valueOf(q.getWantedId());
    }

    /**
     * Resolve an unmet-item key (a concrete item or a tag such as
     * questown:villager_food) to the name a player reads in the need bubble.
     */
    private static String resolveItemName(String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return null;
        }
        try {
            Ingredient ing = Ingredients.fromString(itemKey);
            return Ingredients.getName(ing).getString();
        } catch (Exception ignored) {
            // not an item or tag; fall through
        }
        return null;
    }

    /** The flag at the given position, or the first flag if none is at that spot. */
    private static TownFlagBlockEntity flagAt(BlockPos pos) {
        for (TownFlagBlockEntity f : TownFlags.all()) {
            if (f.getBlockPos() != null && f.getBlockPos().equals(pos)) {
                return f;
            }
        }
        // Fall back to the first flag so an agent inspecting the flag's own
        // position (or a small offset) still gets a view.
        for (TownFlagBlockEntity f : TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}
