package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

// Dev-only: perform the real right-click a player would, server-side, so a headless
// held run can drive the chicken-arc interaction beats (get the wand from the flag,
// open a villager or the flag menu, hand seeds to the chicken) the way a player
// does, not by flipping a bit. It puts the item in the named player's main hand and
// invokes the actual block interaction (gameMode.useItemOn), the same real path the
// game uses, so the agent hits the same walls a player does. No permission gate, so
// it runs from the console (FIFO). The player is the nearest live player to the
// target, matching the arc's nearest-player search.
public class QtDevClickCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src, CommandBuildContext ctx) {
        RequiredArgumentBuilder<CommandSourceStack, ItemInput> item =
                Commands.argument("item", ItemArgument.item(ctx));
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos", BlockPosArgument.blockPos()
        );

        // @formatter:off
        var qtdev = Commands.literal("_qtdev");
        var click = Commands.literal("click")
                .then(item
                    .then(posArg
                        .executes(css -> run(
                                css.getSource(),
                                ItemArgument.getItem(css, "item").getItem(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                        ))));
        var clickwand = Commands.literal("clickwand")
                .then(posArg
                    .executes(css -> runWand(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                    )));
        qtdev.then(click).then(clickwand);
        src.register(qtdev);
        // @formatter:on
    }

    /**
     * The wand beat as a player does it: right-click the flag with a stick, which
     * the flag converts into a town wand. This is the real flag interaction
     * (gameMode.useItemOn on the flag block's use()), not a bit flip.
     */
    private static int runWand(CommandSourceStack source, BlockPos flagPos) {
        ServerPlayer player = findPlayer(source, flagPos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[click wand] no player near {}", flagPos);
            return 1;
        }
        // Give the player a stick in the main hand, then do the real flag click.
        // The flag converts the stick to a town wand in the player's hand
        // (TownFlagBlock.use -> convertItemInHand); that conversion must persist,
        // so we do NOT restore the previous hand item afterwards.
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

    private static int run(CommandSourceStack source, Item item, BlockPos pos) {
        ServerPlayer player = findPlayer(source, pos);
        if (player == null) {
            QT.FLAG_LOGGER.warn("[click] no player near {}", pos);
            return 1;
        }
        ServerLevel level = source.getLevel();
        ItemStack stack = new ItemStack(item);
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            InteractionResult result = player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)
            );
            QT.FLAG_LOGGER.info(
                    "[click] {} right-clicked {} with {} -> {}",
                    player.getGameProfile().getName(), pos, item, result
            );
            return 0;
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
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
