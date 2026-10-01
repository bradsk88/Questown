package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.entity.TownFlags;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.UUID;

// Dev-only: open the assigned villager's tabbed menu for the nearest player —
// the flag right-click path (showMultiStatusUI / showUI) without input injection.
// Drives the port-test GUI captures headlessly. No permission gate (console/FIFO).
public class OpenVillagerMenuDevCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev")
                .then(Commands.literal("openvillager")
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                new String[]{OpenVillagerMenuMessage.INVENTORY, OpenVillagerMenuMessage.QUESTS,
                                        OpenVillagerMenuMessage.STATS, OpenVillagerMenuMessage.SKILLS,
                                        OpenVillagerMenuMessage.ECONOMICS, OpenVillagerMenuMessage.BOP}, b))
                        .executes(css -> run(css.getSource(), css.getArgument("type", String.class)))))
                .then(Commands.literal("townplace")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(css -> runTownPlace(css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")))))
                .then(Commands.literal("townspawn")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(css -> runTownSpawn(css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")))))
                .then(Commands.literal("open_jobs")
                    .executes(css -> runOpenJobs(css.getSource())))
        );
        // @formatter:on
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

    private static int run(CommandSourceStack source, String type) {
        if (TownFlags.all().isEmpty()) {
            QT.FLAG_LOGGER.warn("[openvillager] no flags registered");
            return 1;
        }
        TownFlagBlockEntity flag = null;
        for (TownFlagBlockEntity f : TownFlags.all()) {
            if (f.getVillagerHandle().entities().iterator().hasNext()) {
                flag = f;
                break;
            }
        }
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[openvillager] no flag has an assigned villager yet");
            return 1;
        }
        ServerPlayer player = nearestPlayerTo(net.minecraft.world.phys.Vec3.atCenterOf(flag.getBlockPos()), source.getLevel());
        if (player == null) {
            QT.FLAG_LOGGER.warn("[openvillager] no player to open the menu for");
            return 1;
        }
        UUID villager = flag.getVillagerHandle().entities().iterator().next().getUUID();
        // All types open the per-villager TABBED menu (VillagerTabsEmbedding),
        // so the client-side port-test hook can read flag pos + villager UUID
        // from it and drive `tab` switches. INVENTORY is the tabbed parent.
        flag.getVillagerHandle().showUI(player, type, villager);
        QT.FLAG_LOGGER.info("[openvillager] {} menu for {} (villager {})", type,
                player.getGameProfile().getName(), villager);
        return 0;
    }

    /**
     * Open the job board (WorkScreen) for the nearest player: the flag's
     * {@code openJobsMenu} owner path (AddWork/RemoveWork flows reach the same
     * call), so no board block or hand item is needed for a console/FIFO drive.
     */
    private static int runOpenJobs(CommandSourceStack source) {
        if (TownFlags.all().isEmpty()) {
            QT.FLAG_LOGGER.warn("[open_jobs] no flags registered");
            return 1;
        }
        TownFlagBlockEntity flag = TownFlags.all().iterator().next();
        ServerPlayer player = nearestPlayerTo(
                net.minecraft.world.phys.Vec3.atCenterOf(flag.getBlockPos()), source.getLevel());
        if (player == null) {
            QT.FLAG_LOGGER.warn("[open_jobs] no player near {}", flag.getBlockPos());
            return 1;
        }
        flag.openJobsMenu(player, false);
        QT.FLAG_LOGGER.info("[open_jobs] work board for {} (flag {})",
                player.getGameProfile().getName(), flag.getBlockPos());
        return 0;
    }

    private static ServerPlayer nearestPlayerTo(
            net.minecraft.world.phys.Vec3 near, net.minecraft.world.level.Level level) {
        return level.players().stream()
                .filter(p -> p instanceof ServerPlayer sp)
                .map(p -> (ServerPlayer) p)
                .min(Comparator.comparingDouble(p -> p.position().distanceToSqr(near)))
                .orElse(null);
    }
}
