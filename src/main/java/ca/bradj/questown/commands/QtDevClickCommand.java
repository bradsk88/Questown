package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.world.item.Item;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import ca.bradj.questown.town.entity.TownFlags;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// Dev-only: a real right-click on a block by the nearest player, with an item in their hand.
public class QtDevClickCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src, CommandBuildContext itemCtx) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos", BlockPosArgument.blockPos()
        );

        // @formatter:off
        var qtdev = Commands.literal("_qtdev");
        // openvillager: open a villager's menu for the nearest player.
        var openvillager = Commands.literal("openvillager")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                            new String[]{OpenVillagerMenuMessage.INVENTORY, OpenVillagerMenuMessage.QUESTS,
                                    OpenVillagerMenuMessage.STATS, OpenVillagerMenuMessage.SKILLS,
                                    OpenVillagerMenuMessage.ECONOMICS, OpenVillagerMenuMessage.BOP}, b))
                    .executes(css -> runOpenVillager(css.getSource(), css.getArgument("type", String.class))));
        var openJobs = Commands.literal("open_jobs")
                .executes(css -> runOpenJobs(css.getSource()));
        // board_request: post one work request to the job board.
        var boardRequest = Commands.literal("board_request")
                .then(Commands.argument("item", ItemArgument.item(itemCtx))
                    .executes(css -> runBoardRequest(
                            css.getSource(),
                            ItemArgument.getItem(css, "item").getItem()
                    )));
        // show_item_jobs: open the item-jobs screen for the first villager's job.
        var showItemJobs = Commands.literal("show_item_jobs")
                .executes(css -> runShowItemJobs(css.getSource()));
        var clickwand = Commands.literal("clickwand")
                .then(posArg
                    .executes(css -> runWand(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                    )));
        // clickheld: right-click a block with whatever the player holds, keeping its NBT. For the
        // campfire sleep: set night, then clickheld the campfire from near the bed.
        var clickheld = Commands.literal("clickheld")
                .then(posArg
                    .executes(css -> runHeld(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                    )));
        // townplace / townspawn: a flag and a villager, for screen captures.
        var townplace = Commands.literal("townplace")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(css -> runTownPlace(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                    )));
        var townspawn = Commands.literal("townspawn")
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(css -> runTownSpawn(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                    )));
        qtdev.then(clickwand).then(clickheld).then(openvillager).then(townplace).then(townspawn).then(openJobs).then(boardRequest).then(showItemJobs);
        src.register(qtdev);
        // @formatter:on
    }

    /** The wand lesson as a player does it: right-click the flag with a stick. */
    private static int runWand(CommandSourceStack source, BlockPos flagPos) {
        ServerPlayer player = findPlayer(source, flagPos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[click wand] no player near {}", flagPos);
            return 1;
        }
        // The flag turns the stick into a wand in the player's hand; leave it there.
        player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.Items.STICK.getDefaultInstance());
        InteractionResult result = player.gameMode.useItemOn(
                player,
                source.getLevel(),
                player.getMainHandItem(),
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(flagPos), Direction.UP, flagPos, false)
        );
        QT.FLAG_LOGGER.info(
                "[click wand] {} right-clicked flag {} with a stick -> {} (now holds {})",
                player.getGameProfile().getName(), flagPos, result,
                player.getMainHandItem().getItem().toString()
        );
        return 0;
    }

    /** Right-click a block with the held item, keeping its NBT. The hand is not restored. */
    private static int runHeld(CommandSourceStack source, BlockPos pos) {
        ServerPlayer player = findPlayer(source, pos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[click held] no player near {}", pos);
            return 1;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            QT.FLAG_LOGGER.warn("[click held] {} has nothing in the main hand", player.getGameProfile().getName());
            return 1;
        }
        InteractionResult result = player.gameMode.useItemOn(
                player,
                source.getLevel(),
                held,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
        );
        QT.FLAG_LOGGER.info(
                "[click held] {} right-clicked {} with held {} -> {}",
                player.getGameProfile().getName(), pos, held.getItem().toString(), result
        );
        return 0;
    }

    /** Open the assigned villager's menu for the nearest player: "inventory" opens the tabbed menu, anything else that tab. */
    private static int runOpenVillager(CommandSourceStack source, String type) {
        if (TownFlags.all().isEmpty()) {
            QT.FLAG_LOGGER.warn("[openvillager] no flags registered");
            return 1;
        }
        TownFlagBlockEntity flag = TownFlags.all().iterator().next();
        BlockPos pos = flag.getBlockPos();
        ServerPlayer player = findPlayer(source, pos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[openvillager] no player near {}", pos);
            return 1;
        }
        var holder = flag.getVillagerHandle();
        if (holder.entities().isEmpty()) {
            QT.FLAG_LOGGER.warn("[openvillager] flag {} has no assigned villager", pos);
            return 1;
        }
        UUID villager = holder.entities().iterator().next().getUUID();
        holder.showUI(player, type, villager);
        QT.FLAG_LOGGER.info("[openvillager] {} menu for {} (villager {})", type,
                player.getGameProfile().getName(), villager);
        return 0;
    }

    /** Open the job board for the nearest player. */
    private static int runOpenJobs(CommandSourceStack source) {
        if (TownFlags.all().isEmpty()) {
            QT.FLAG_LOGGER.warn("[open_jobs] no flags registered");
            return 1;
        }
        TownFlagBlockEntity flag = TownFlags.all().iterator().next();
        BlockPos pos = flag.getBlockPos();
        ServerPlayer player = findPlayer(source, pos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[open_jobs] no player near {}", pos);
            return 1;
        }
        flag.openJobsMenu(player, false);
        QT.FLAG_LOGGER.info("[open_jobs] work board for {} (flag {})", player.getGameProfile().getName(), pos);
        return 0;
    }

    private static int runBoardRequest(CommandSourceStack source, Item item) {
        if (TownFlags.all().isEmpty()) {
            QT.FLAG_LOGGER.warn("[board_request] no flags registered");
            return 1;
        }
        TownFlagBlockEntity flag = TownFlags.all().iterator().next();
        flag.requestWorkFromPlayer(ca.bradj.questown.jobs.requests.WorkRequest.of(item));
        QT.FLAG_LOGGER.info("[board_request] work request posted for {} (flag {})", item, flag.getBlockPos());
        return 0;
    }

    private static int runShowItemJobs(CommandSourceStack source) {
        if (TownFlags.all().isEmpty()) {
            QT.FLAG_LOGGER.warn("[show_item_jobs] no flags registered");
            return 1;
        }
        TownFlagBlockEntity flag = TownFlags.all().iterator().next();
        BlockPos pos = flag.getBlockPos();
        ServerPlayer player = findPlayer(source, pos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[show_item_jobs] no player near {}", pos);
            return 1;
        }
        VisitorMobEntity townie = flag.getVillagerHandle().entities().stream()
                .filter(VisitorMobEntity.class::isInstance)
                .map(VisitorMobEntity.class::cast)
                .findFirst()
                .orElse(null);
        if (townie == null) {
            QT.FLAG_LOGGER.warn("[show_item_jobs] no townie with a job at {}", pos);
            return 1;
        }
        flag.getVillagerHandle().showJobUI(player, townie.getJobId());
        QT.FLAG_LOGGER.info("[show_item_jobs] item-jobs screen for {} (job {} / flag {})",
                player.getGameProfile().getName(), townie.getJobId(), pos);
        return 0;
    }

    private static int runTownPlace(CommandSourceStack source, BlockPos pos) {
        source.getLevel().setBlockAndUpdate(
                pos, BlocksInit.COBBLESTONE_TOWN_FLAG.get().defaultBlockState());
        QT.FLAG_LOGGER.info("[townplace] flag placed at {}", pos);
        return 0;
    }

    private static int runTownSpawn(CommandSourceStack source, BlockPos pos) {
        if (!(source.getLevel().getBlockEntity(pos) instanceof TownFlagBlockEntity flag)) {
            QT.FLAG_LOGGER.warn("[townspawn] no town flag block entity at {}", pos);
            return 1;
        }
        if (flag.getVillagerHandle().entities().iterator().hasNext()) {
            QT.FLAG_LOGGER.info("[townspawn] flag {} already has a villager", pos);
            return 0;
        }
        SpawnVisitorReward.spawnNew(flag);
        QT.FLAG_LOGGER.info("[townspawn] villager spawned for flag {}", pos);
        return 0;
    }

    private static ServerPlayer findPlayer(CommandSourceStack source, BlockPos near) {
        ServerLevel level = source.getLevel();
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            double dx = p.getX() - (near.getX() + 0.5D);
            double dz = p.getZ() - (near.getZ() + 0.5D);
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
