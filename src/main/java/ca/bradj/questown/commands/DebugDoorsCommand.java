package ca.bradj.questown.commands;

import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DebugDoorsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qtdebugdoors")
                .requires((p_137812_) -> {
                    return p_137812_.hasPermission(2);
                })
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(css -> {
                            return giveDebug(css.getSource(), BlockPosArgument.getLoadedBlockPos(css, "pos"));
                        })));
    }

    private static int giveDebug(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tfbe)) {
            // TODO: Better error handling?
            return -1;
        }

        ItemStack debugItem = ItemsInit.TOWN_DOOR_TESTER.get()
                                                              .getDefaultInstance();
        TownFlagBlock.StoreParentOnNBT(
                debugItem,
                target
        );
        try {
            source.getPlayerOrException().getInventory().add(debugItem);
        } catch (CommandSyntaxException ex) {
            throw new RuntimeException(ex);
        }
        return 0;
    }
}
