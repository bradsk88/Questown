package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.mobs.helperchicken.ChickenArcConditions;
import ca.bradj.questown.mobs.helperchicken.ChickenArcTransitions;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
import ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets;
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
        inspect.then(Commands.literal("doors")
                .then(posArg().executes(css -> runTab(css.getSource(),
                        BlockPosArgument.getLoadedBlockPos(css, "pos"), "doors"))));
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
        // doors is not a menu tab (a player does not see it in the flag menu); it is
        // the agent's read of the door-trouble signal, so it bypasses the shown check.
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

    /**
     * The chicken's current beat. Narrated two ways: first as the chicken's
     * observed behavior (the new-player vantage the command is meant to be),
     * then as the agent's actionable guidance - the action a new player would
     * do, the concrete place it goes, and, where no console action can do the
     * beat, a plain "needs a player" note. The two are drawn from the same
     * {@link AgentBeatGuidance} source as {@code _qtdev help} so they cannot
     * drift, and so the agent is never routed to an observable-only line
     * ambiguous enough to push it into a code-derived substitute (the job-board
     * for a sign gap). See the filed issues under
     * .scratch/chicken-arc-campfire-sleep/.
     */
    private static int runChicken(CommandSourceStack source, BlockPos pos) {
        TownFlagBlockEntity flag = flagAt(pos);
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[_qtdev inspect] no town flag at {}", pos);
            return 1;
        }
        if (!flag.getChickenEverSpawned()) {
            QT.FLAG_LOGGER.warn("[_qtdev inspect] no helper chicken is spawned for flag {} "
                    + "(the onboarding arc is inert; start it with _qtdev spawn_chicken {})",
                    flag.getTownFlagBasePos(), flag.getTownFlagBasePos());
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
        AgentBeatGuidance.Guidance guidance = AgentBeatGuidance.forBeat(state);
        if (guidance != null) {
            QT.FLAG_LOGGER.info("[_qtdev inspect] to do: {}", guidance.need());
            QT.FLAG_LOGGER.info("[_qtdev inspect] how:   {}", guidance.how());
            String place = whereToAct(flag, state);
            if (place != null) {
                QT.FLAG_LOGGER.info("[_qtdev inspect] where: {}", place);
            }
            if (needsLivePlayer(state)) {
                QT.FLAG_LOGGER.info("[_qtdev inspect] gap:   this beat needs a live player (no console "
                        + "action can do it); do not substitute a code-derived action.");
            }
        }
        return 0;
    }

    /**
     * Beats a held run can drive only from a live player, not from the console
     * command set: opening a villager's or the flag's menu (the agent has no
     * menu-open action), and handing the Worldly Seeds to the chicken. The
     * campfire-sleep beat is NOT here: its clock skip is an agent action
     * (`time set`), so it is not a pure player beat. Surfacing the gap plainly
     * is what keeps the agent from filling it with a code-derived substitute
     * (the job-board-for-a-sign gap) - see .scratch/chicken-arc-campfire-sleep/.
     */
    private static boolean needsLivePlayer(ChickenBeatState state) {
        return state == ChickenBeatState.WAITING_FOR_VILLAGER_UI
                || state == ChickenBeatState.WAITING_FOR_FLAG_UI
                || state == ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY;
    }

    /**
     * The concrete place the current beat acts, in the world. Points the agent
     * at the block the chicken pecks at (the real location a new player would
     * act on), and - for the beats that act inside a registered room - tells
     * the agent to register the room first when none is, instead of assuming a
     * registered room already exists. Returns null for beats with no single
     * world target (the campfire sleep, the UI opens).
     */
    private static String whereToAct(TownFlagBlockEntity flag, ChickenBeatState state) {
        BlockPos target = HelperChickenBeatOffsets.resolveTarget(state, flag.getTownFlagBasePos(),
                flag.getChickenStructureRotation());
        if (target == null) {
            return null;
        }
        boolean noRoom = flag.getRoomHandle().getAllRegisteredDoors().isEmpty();
        return switch (state) {
            // These act inside a registered room; if none is registered the block
            // the chicken pecks at is not yet a room, so say so instead of
            // pointing at an empty cell.
            case WAITING_FOR_SIGN -> {
                // A registered door with no room is a dead door (ADR-0011); the cell the
                // chicken pecks at is rotation-anchored and is not the room, so surface the
                // trouble and, for NO_RECIPE, where the room actually is.
                String deadDoor = deadDoorGuidance(flag);
                if (deadDoor != null) {
                    yield deadDoor;
                }
                if (noRoom) {
                    yield "no room is registered yet - register the room's door first "
                            + "(right-click the door with the town wand, or use the register-room "
                            + "command), then put a sign inside it.";
                }
                yield "the registered room - put a sign inside it (the chicken pecks at "
                        + target.toShortString() + ").";
            }
            case WAITING_FOR_CHEST -> {
                // The chicken brings and drops the chest itself; the player only needs to
                // stand in the room to watch it (the worked example), so never read this as
                // "put a chest". No behind-the-curtain terms (container / recipe).
                yield "the room the chicken is standing in - stand in it and watch the "
                        + "chicken drop a chest there.";
            }
            case WAITING_FOR_WALL_BLOCK ->
                    "the gap the chicken pecks at (" + target.toShortString() + ")";
            case WAITING_FOR_DOOR, WAITING_FOR_WAND_ON_DOOR ->
                    "the door at " + target.toShortString();
            case WAITING_FOR_ROOM_BUILD ->
                    "the door the chicken is building a room around (" + target.toShortString() + ")";
            case WAITING_FOR_PRESSURE_PLATE ->
                    "the gate at " + target.toShortString()
                            + " (craft the welcome mat at the flag first)";
            default ->
                    null;
        };
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
            case WAITING_FOR_CHEST -> "The chicken brings a chest into the room. "
                    + "[Stand in the room and watch the chicken drop a chest]";
            case WAITING_FOR_ROOM_BUILD -> "The chicken pecks at the door and lays blocks "
                    + "around it. [The chicken builds a room around the door — watch it]";
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

    /**
     * The door-trouble read (ADR-0011): each registered door and why it has no
     * room - NOT_ENCLOSED (walls or roof incomplete) or NO_RECIPE (enclosed, but
     * the contents match no room recipe). For NO_RECIPE the room exists, so the
     * range is printed (where the player must put the recipe block); a door with
     * a room is shown as such.
     */
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

    /**
     * For the sign/chest beats, if a registered door has no room the beat can not
     * be met at the rotation-anchored cell the chicken pecks at. Returns a
     * guidance string naming the trouble and, for NO_RECIPE, the room's position
     * range (where the block must go); null when every registered door has a room.
     */
    private static String deadDoorGuidance(TownFlagBlockEntity flag) {
        for (var e : flag.getDeadDoors().entrySet()) {
            DoorTrouble trouble = e.getValue();
            String at = "the door at " + e.getKey().toShortString();
            if (trouble == DoorTrouble.NO_RECIPE) {
                String range = flag.getRoomRangeForDoor(e.getKey())
                        .map(r -> "the room is " + r + " - put the sign/chest inside it")
                        .orElse("the room has no position range");
                return at + " has no recipe-matched room (its contents match no recipe); "
                        + range;
            }
            return at + " is not enclosed (walls or roof incomplete) - build the room "
                    + "around it first, then put the sign/chest inside it.";
        }
        return null;
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
