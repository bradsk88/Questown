package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.gui.FlagTabsEmbedding;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.UIQuest;
import ca.bradj.questown.mobs.helperchicken.ChickenArcConditions;
import ca.bradj.questown.mobs.helperchicken.ChickenArcUiObservations;
import ca.bradj.questown.mobs.helperchicken.ChickenBeatState;
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
 * enum, a {@code status}/condition line, or the arc map. The action (placing a
 * block, right-clicking a block) stays a separate command. Inspecting a
 * villager or the flag menu does set the corresponding chicken-arc UI
 * observation flag, so the beat advances the way it would for a player looking
 * at the same thing.
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
        ChickenArcUiObservations.markFlagUiOpened(source.getLevel(), flag.getTownFlagBasePos());
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
            case "villagers" -> {
                ChickenArcUiObservations.markVillagerUiOpened(
                        source.getLevel(), flag.getTownFlagBasePos());
                printVillagers(flag);
            }
            case "quests" -> printQuests(flag);
            case "economy" -> printEconomy(flag);
            case "blocks" -> printBlocks(flag);
            case "crafting" -> printCrafting(flag);
            default -> QT.FLAG_LOGGER.warn("[_qtdev inspect] unknown tab {}", tab);
        }
        return 0;
    }

    /**
     * The chicken's current beat. This is the chicken's observed behavior - the
     * new-player vantage the command is meant to be - plus what is in the player's
     * hand. It is hand-holding (the chicken points at something and the player
     * watches what happens), not an answer: it never prints what the town wants
     * (the {@code to do}), the exact action or its place (the {@code how} / {@code where}),
     * or a "needs a live player" note. Those are behind the curtain; a player reads
     * the world (the quest, the room, the villager's need) and decides, and the
     * actionable guidance lives in the {@code _qtdev help} cheatsheet, not here. The
     * old version printed the actionable guidance off the same {@link AgentBeatGuidance}
     * source as {@code _qtdev help}, so it could not drift - but drifting was the
     * point, a hand-holding cue that told the agent the answer instead of letting it
     * read the world. See the filed issues under .scratch/chicken-arc-campfire-sleep/.
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
        // The player's held item, the way a new player looks down at their own hand.
        // This is the eye a blind _qtdev clickheld lacks: it acts with whatever is
        // held, so the agent must be able to see what that is and correct it, rather
        // than retry clickheld at new spots hoping the held item is the one the beat
        // wants. A no-op clickheld is usually "wrong hand," not "wrong spot."
        printHeld(source, flag);
        return 0;
    }

    /**
     * What the player is holding in the main hand - the "look at my hand" a new
     * player has but a headless agent does not. Lets the agent match the
     * chicken's "use your held X" hint to the actual held item and correct it
     * (e.g. the town wand is in the hand but the beat wants a sign), instead of
     * blind-retrying _qtdev clickheld. Empty when the hand is empty.
     */
    private static void printHeld(CommandSourceStack source, TownFlagBlockEntity flag) {
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        if (player == null) {
            QT.FLAG_LOGGER.info("[_qtdev inspect] held: (no player near the flag)");
            return;
        }
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        QT.FLAG_LOGGER.info("[_qtdev inspect] held: {}",
                held.isEmpty() ? "(empty)" : held.getHoverName().getString());
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
            case WAITING_FOR_WAND_ON_CAMPFIRE -> {
                boolean campfireLit = ChickenArcConditions.isCampfireLit(
                        flag.getServerLevel(), flag.getTownFlagBasePos()
                );
                if (campfireLit) {
                    yield holdsWand
                            ? "The chicken looks at the lit campfire near the flag. [Use the town wand on the campfire]"
                            : "The chicken looks at the lit campfire near the flag, then at your empty hand. "
                                    + "[Hold the town wand and use it on the campfire]";
                }
                yield holdsWand
                        ? "The chicken looks at an empty spot near the flag, then at your town wand. "
                                + "[Place and light a campfire near the flag]"
                        : "The chicken looks at an empty spot near the flag, then at your empty hand. "
                                + "[Place and light a campfire near the flag, then hold the town wand]";
            }
            case SUNSET_AND_MAP -> "The chicken looks at the dark sky, then at the campfire. "
                    + "[Come back at night and sleep on the campfire with the wand]";
            case WAITING_FOR_WALL_BLOCK -> "The chicken pecks at a gap in the wall. [Put a solid "
                    + "block in the gap the chicken pecks at]";
            case WAITING_FOR_DOOR -> "The chicken points at a spot near the flag. [Put a door "
                    + "near the flag - _qtdev place_door with no coordinate drops it in the "
                    + "right spot]";
            case WAITING_FOR_WAND_ON_DOOR -> holdsWand
                    ? "The chicken looks at the door, then at your town wand. [Use the town wand "
                            + "on the door]"
                    : "The chicken looks at the door, then at your empty hand. [Hold the town wand "
                            + "and use it on the door]";
            case WAITING_FOR_SIGN -> signInRoomLine(flag);
            case WAITING_FOR_CHEST -> "The chicken brings a chest into the room. "
                    + "[Stand in the room and watch the chicken drop a chest]";
            case WAITING_FOR_ROOM_BUILD -> "The chicken pecks at the door and lays blocks "
                    + "around it. [The chicken builds a room around the door — watch it]";
            case WAITING_FOR_PRESSURE_PLATE -> "The chicken looks at the gate, then at your hand. "
                    + "[Make a welcome mat and put it on the gate]";
            case WAITING_FOR_FREE_FOOD -> "The chicken looks at your hand, then at the flag. "
                    + "[Feed the flag free food]";
            case WAITING_FOR_VILLAGER_UI -> "The chicken looks at a villager in the town. [Look at "
                    + "a villager]";
            case WAITING_FOR_FLAG_UI -> "The chicken looks at the flag. [Look at the flag]";
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> "The chicken looks back at the flag. [A room "
                    + "quest is still open - look at the quests for which room it needs]";
            case COMPLETE, FORFEIT -> "";
        };
    }

    /**
     * The sign beat names a spot the sign can go, the way a player would see the
     * room's open floor. The agent places into that cell instead of guessing a
     * coordinate (a guessed spot lands on a wall or outside the room, so it does
     * not count, and an agent placing with setblock breaks the wall it lands on).
     * The spot is the first empty interior cell of a registered room, the same
     * cells {@code _qtdev place_block} lists - guaranteed inside a room.
     */
    private static String signInRoomLine(TownFlagBlockEntity flag) {
        BlockPos cell = flag.interiorCells().stream().findFirst().orElse(null);
        if (cell == null) {
            return "The chicken looks into the room and pecks. [Put a sign in the room - "
                    + "register the door and build a room first]";
        }
        return "The chicken looks into the room and pecks. [Put a sign in the room - at "
                + cell.getX() + ", " + cell.getY() + ", " + cell.getZ() + "]";
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
            // The live job status (seeking work, hunting, dropping loot),
            // what a player reads off the villager; job= is the job id, not
            // the live status.
            try {
                ca.bradj.questown.jobs.IStatus<?> status = villager.getStatusForServer();
                if (status != null) {
                    // ProductionStatus.name() is the sync ordinal, not a
                    // readable name; its `name` field holds the human one.
                    String label = status instanceof ca.bradj.questown.jobs.production.ProductionStatus ps
                            ? ps.name : status.nameV2();
                    QT.FLAG_LOGGER.info("[_qtdev inspect]     status: {}", label);
                }
            } catch (Exception ignored) {
                // status is best-effort eyes, never a reason to fail the inspect
            }
            // Work-site progress (processing state, work/time ticks left): how a
            // player watching a leaver tells a genuine mid-trip (timer still
            // counting down) from a trip cut short. Best-effort like status.
            try {
                String progress = villager.getWorkProgressDebug();
                if (progress != null) {
                    QT.FLAG_LOGGER.info("[_qtdev inspect]     progress: {}", progress);
                }
            } catch (Exception ignored) {
                // best-effort eyes, never a reason to fail the inspect
            }
            // Eyes-level state a player sees on the villager's body: full hands
            // (carried loot) and a Block of Progress bobbing above their head.
            // Without this an agent reads need=NONE on a villager who is stuck
            // holding goods they cannot deposit, and the stall is invisible.
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
        // The command a player cannot see but the agent needs named: the change
        // job tab's action, as a console hand. The tab itself is player-facing;
        // this line only says the hand exists and what it costs.
        QT.FLAG_LOGGER.info("[_qtdev inspect]   to change a villager's job: _qtdev change_job <villager uuid> "
                + "- spends a Block of Progress from the flag and requests a job change (takes effect overnight)");
    }

    /** What a player sees in the villager's hands: the non-empty stacks they
     * carry, as "Nx item" entries. Empty string when their hands are empty. */
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

    /** Whether the town's registered containers can take one more item, the
     * fact a player reads by opening the chests. Same rule as the deposit path
     * (an empty slot anywhere); stacked-but-not-full slots are not counted,
     * matching how the deposit code actually inserts. */
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
            // The quest's flavor text, the guidance the quest UI shows a player.
            if (q.getFlavorText() != null && !q.getFlavorText().isEmpty()) {
                QT.FLAG_LOGGER.info("[_qtdev inspect]     {}", q.getFlavorText());
            }
            if (q.getType() == Quest.QuestType.ROOM) {
                // The blocks the room must contain, the way the quest UI shows the
                // room recipe as icons: a player reads those and knows what to put
                // in the room, so the agent gets the same read here. A tag entry
                // also lists a few example items that count.
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
                                + "register a door and lay a room shell around it, the way the "
                                + "helper chicken builds one, then put this quest's recipe block "
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
        // ITEM and LOOT both carry their want as ingredients (an item or a tag); LOOT's
        // are satisfied by production rather than a chest deposit, but the "wants" line
        // reads the same.
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
        return String.valueOf(q.getWantedId());
    }

    /** One ingredient of a room recipe, as a player reads it in the quest UI:
     * the item or tag name (tags resolve to a readable name, e.g. the
     * light_sources tag reads "Light Source").
     */
    private static String describeIngredient(Ingredient ing) {
        return Ingredients.getName(ing).getString();
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
