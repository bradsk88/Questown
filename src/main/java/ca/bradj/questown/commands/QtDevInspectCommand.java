package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.entity.TownFlags;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Quest;
import ca.bradj.questown.town.rooms.DoorTrouble;
import ca.bradj.questown.town.rooms.TownPosition;

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

/** Dev-only, read-only: what a player sees on right-clicking a flag. */
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
        inspect.then(Commands.literal("doors")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "doors"))));
        // TODO: `_qtdev inspect chicken` showed the helper chicken's lesson. For its implementation see #409, #410, #411 and #412.
        src.register(Commands.literal("_qtdev").then(inspect));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.coordinates.Coordinates> posArg() {
        return Commands.argument("pos", BlockPosArgument.blockPos());
    }

    /** The flag menu's tabs, with red dots, and their content. */
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

    private static int runTab(
            CommandSourceStack source, BlockPos pos, String tab
    ) {
        TownFlagBlockEntity flag = flagAt(pos);
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[_qtdev inspect] no town flag at {}", pos);
            return 1;
        }
        // doors is not a menu tab, so it is always shown.
        if (tab.equals("doors")) {
            printDoors(flag);
            return 0;
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

    private static void listTabs(FlagTabsEmbedding.FlagInfo info, TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect]   villagers {}{}",
                info.showVillagersTab() ? "shown" : "hidden", "");
        QT.FLAG_LOGGER.info("[_qtdev inspect]   quests {}{}",
                info.showQuestsTab() ? "shown" : "hidden",
                info.questsTabAttention() ? " (red-dot)" : "");
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
            // The villager's live status (seeking work, hunting, dropping loot), not its job id.
            try {
                ca.bradj.questown.jobs.IStatus<?> status = villager.getStatusForServer();
                if (status != null) {
                    // name() is the sync ordinal; the readable name is the `name` field.
                    String label = status instanceof ca.bradj.questown.jobs.production.ProductionStatus ps
                            ? ps.name : status.nameV2();
                    QT.FLAG_LOGGER.info("[_qtdev inspect]     status: {}", label);
                }
            } catch (Exception ignored) {
            }
            // Work left and time left: tells a villager mid-trip from one whose trip was cut short.
            try {
                String progress = villager.getWorkProgressDebug();
                if (progress != null) {
                    QT.FLAG_LOGGER.info("[_qtdev inspect]     progress: {}", progress);
                }
            } catch (Exception ignored) {
            }
            // What the villager carries, and its Block of Progress.
            String carrying = describeCarrying(villager);
            boolean hasLoot = !carrying.isEmpty();
            if (hasLoot) {
                QT.FLAG_LOGGER.info("[_qtdev inspect]     carrying: {}", carrying);
            }
            boolean hasBop = villager.hasBlockOfProgress();
            if (hasBop) {
                QT.FLAG_LOGGER.info("[_qtdev inspect]     holding a Block of Progress (visible above their head)");
            }
            if (hasLoot || hasBop) {
                switch (townContainerSpace(flag)) {
                    case ALL_FULL -> QT.FLAG_LOGGER.info(
                            "[_qtdev inspect]     cannot deposit: every town chest is full - make space or build another store room");
                    case NO_CONTAINER -> QT.FLAG_LOGGER.info(
                            "[_qtdev inspect]     cannot deposit: the town has no chest - build a store room");
                    case HAS_SPACE -> { }
                }
            }
            if (need == TownieNeed.UNMET_ITEM) {
                String name = resolveItemName(villager.getUnmetItem());
                QT.FLAG_LOGGER.info("[_qtdev inspect]     needs item: {}{}",
                        name != null ? name : villager.getUnmetItem(),
                        name != null ? " (" + villager.getUnmetItem() + ")" : "");
            }
        }
        if (!any) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   no villagers");
            return;
        }
        QT.FLAG_LOGGER.info("[_qtdev inspect]   to change a villager's job: _qtdev change_job <villager uuid> "
                + "- spends a Block of Progress from the flag and requests a job change (takes effect overnight)");
    }

    /** The villager's carried stacks as "Nx item", or empty. */
    private static String describeCarrying(VisitorMobEntity villager) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        net.minecraft.world.Container inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                parts.add(s.getCount() + "x " + s.getItem());
            }
        }
        return String.join(", ", parts);
    }

    private enum ContainerSpace { HAS_SPACE, ALL_FULL, NO_CONTAINER }

    /** Whether the town's chests have an empty slot. */
    private static ContainerSpace townContainerSpace(TownFlagBlockEntity flag) {
        boolean sawContainer = false;
        for (ca.bradj.questown.jobs.leaver.ContainerTarget<ca.bradj.questown.integration.minecraft.MCContainer, ca.bradj.questown.integration.minecraft.MCTownItem> target :
                ca.bradj.questown.town.TownContainers.getAllContainers(flag, flag.getServerLevel())) {
            if (flag.getServerLevel().getBlockEntity(target.getBlockPos()) instanceof net.minecraft.world.Container c) {
                sawContainer = true;
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (c.getItem(i).isEmpty()) {
                        return ContainerSpace.HAS_SPACE;
                    }
                }
            }
        }
        return sawContainer ? ContainerSpace.ALL_FULL : ContainerSpace.NO_CONTAINER;
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
            if (q.getFlavorText() != null && !q.getFlavorText().isEmpty()) {
                QT.FLAG_LOGGER.info("[_qtdev inspect]     {}", q.getFlavorText());
            }
            if (q.getType() == Quest.QuestType.ROOM) {
                // The blocks the room needs, as the quest UI shows them, with examples for tags.
                StringBuilder needs = new StringBuilder();
                for (Ingredient ing : q.getIngredients()) {
                    if (needs.length() > 0) {
                        needs.append(", ");
                    }
                    needs.append(describeIngredient(ing));
                }
                QT.FLAG_LOGGER.info("[_qtdev inspect]     room needs: {}",
                        needs.length() == 0 ? "(no recipe found for this room)" : needs);
                QT.FLAG_LOGGER.info(
                        "[_qtdev inspect]     each room quest needs its own room - put this quest's "
                                + "recipe block in a room built for it, not the room for another "
                                + "quest.");
                if (q.status == Quest.QuestStatus.ACTIVE) {
                    QT.FLAG_LOGGER.info(
                            "[_qtdev inspect]     create the room with _qtdev build_room <door pos>: "
                                + "register a door and lay a room shell around it, "
                                + "then put this quest's recipe block "
                                + "inside.");
                }
            }
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

    /** Each registered door and why it has no room: not enclosed, or no room recipe matches. */
    private static void printDoors(TownFlagBlockEntity flag) {
        QT.FLAG_LOGGER.info("[_qtdev inspect] doors tab:");
        var doors = flag.getRoomHandle().getAllRegisteredDoors();
        if (doors.isEmpty()) {
            QT.FLAG_LOGGER.info("[_qtdev inspect]   no registered doors");
            return;
        }
        int flagY = flag.getBlockPos().getY();
        for (TownPosition tp : doors) {
            BlockPos bp = new BlockPos(tp.x, flagY + tp.scanLevel, tp.z);
            DoorTrouble trouble = flag.getDeadDoors().get(bp);
            if (trouble == null) {
                QT.FLAG_LOGGER.info("[_qtdev inspect]   door {} - has a room", bp.toShortString());
                continue;
            }
            QT.FLAG_LOGGER.info("[_qtdev inspect]   door {} - no room ({})",
                    bp.toShortString(), trouble.name());
            if (trouble == DoorTrouble.NO_RECIPE) {
                flag.getRoomRangeForDoor(bp).ifPresent(r ->
                        QT.FLAG_LOGGER.info("[_qtdev inspect]     room range: {}", r));
            }
        }
    }

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
        if (type == Quest.QuestType.ITEM || type == Quest.QuestType.LOOT) {
            return q.getIngredients().isEmpty()
                    ? "item " + q.getWantedId()
                    : Ingredients.toString(q.getIngredients().iterator().next());
        }
        if (type == Quest.QuestType.ROOM) {
            return "room recipe " + q.getWantedId();
        }
        if (type == Quest.QuestType.JOB_CHANGE) {
            return "job: " + q.getWantedId();
        }
        if (type == Quest.QuestType.SKILL_UNLOCK) {
            return "skill tree: " + q.getWantedId();
        }
        if (type == Quest.QuestType.VILLAGER_LEVEL) {
            return "villager level: " + q.getWantedId();
        }
        return String.valueOf(q.getWantedId());
    }

    /** A room-recipe ingredient's name as the quest UI shows it. */
    private static String describeIngredient(Ingredient ing) {
        return Ingredients.getName(ing).getString();
    }

    /** The name a player sees for an item or tag key. */
    private static String resolveItemName(String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return null;
        }
        try {
            Ingredient ing = Ingredients.fromString(itemKey);
            return Ingredients.getName(ing).getString();
        } catch (Exception ignored) {
        }
        return null;
    }

    /** The flag at the position, or else the first flag. */
    private static TownFlagBlockEntity flagAt(BlockPos pos) {
        for (TownFlagBlockEntity f : TownFlags.all()) {
            if (f.getBlockPos() != null && f.getBlockPos().equals(pos)) {
                return f;
            }
        }
        for (TownFlagBlockEntity f : TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}
