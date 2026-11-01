package ca.bradj.questown.commands;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Dev-only: place a block inside a registered room. `_qtdev place_block` lists the room's empty
// interior cells (index, x, y, z); `_qtdev place_block <index> <block>` places the block at that
// cell. A block outside the room's footprint is not one of its contained blocks.
public class FlagPlaceBlockCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("place_block")
                    .executes(css -> list(css.getSource()))
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .then(Commands.argument("block", StringArgumentType.word())
                            .executes(css -> place(
                                css.getSource(),
                                css.getArgument("index", Integer.class),
                                css.getArgument("block", String.class)
                            ))
                        ))
            )
        );
        // @formatter:on
    }

    private static int list(CommandSourceStack source) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_block] no flag to list interior cells for");
            return 1;
        }
        List<BlockPos> cells = flag.interiorCells();
        if (cells.isEmpty()) {
            QT.FLAG_LOGGER.info("[place_block] no empty interior cells in any registered room; register a door and build a room first");
            return 1;
        }
        QT.FLAG_LOGGER.info("[place_block] empty interior cells in registered rooms (index -> x,y,z):");
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            QT.FLAG_LOGGER.info("  {} -> {}, {}, {}", i, p.getX(), p.getY(), p.getZ());
        }
        return 0;
    }

    private static int place(CommandSourceStack source, int index, String blockId) {
        Block block = resolveBlock(blockId);
        if (block == null) {
            QT.FLAG_LOGGER.warn("[place_block] no block named '{}'", blockId);
            return 1;
        }
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[place_block] no flag to place the block for");
            return 1;
        }
        BlockPos pos = flag.placeBlockInInterior(index, block);
        if (pos == null) {
            QT.FLAG_LOGGER.warn("[place_block] no interior cell at index {} for flag {} (no cells or out of range)",
                    index, flag.getBlockPos());
            return 1;
        }
        QT.FLAG_LOGGER.info("[place_block] placed {} at {} (interior cell index {}) for flag {}",
                blockId, pos.toShortString(), index, flag.getBlockPos());
        return 0;
    }

    private static @Nullable Block resolveBlock(String id) {
        Block block = Loader.blockFromId(new ResourceLocation(id));
        if (block == null) {
            block = Loader.blockFromId(new ResourceLocation("minecraft", id));
        }
        return block;
    }

    private static @Nullable TownFlagBlockEntity firstFlag() {
        for (TownFlagBlockEntity f : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}
