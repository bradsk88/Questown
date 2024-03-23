package ca.bradj.questown.commands;

import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class SpawnVillagerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(
                Commands.literal("qtspawn")
                        .requires((p_137812_) -> p_137812_.hasPermission(2))
                        .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(
                                                css -> spawnVillager(
                                                        css.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(css, "pos")
                                                )
                                        )
                        )
        );
    }

    private static int spawnVillager(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel()
                              .getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tfbe)) {
            // TODO: Better error handling?
            return -1;
        }

        tfbe.addImmediateReward(new SpawnVisitorReward(tfbe));
        return 0;
    }
}
