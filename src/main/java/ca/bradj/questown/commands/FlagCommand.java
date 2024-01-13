package ca.bradj.questown.commands;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FlagCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qtflag")
                .requires((p_137812_) -> {
                    return p_137812_.hasPermission(2);
                })
                .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(css -> {
                            return setBlock(css.getSource(), BlockPosArgument.getLoadedBlockPos(css, "pos"));
                        })));
    }

    private static int setBlock(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if ((e instanceof TownFlagBlockEntity)) {
            return 0;
        }

        source.getLevel().setBlockAndUpdate(target.above(), BlocksInit.COBBLESTONE_TOWN_FLAG.get().defaultBlockState());
        return 0;
    }
}
