package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;

public class TimeWarpCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qtwarp")
                .requires((p_137812_) -> {
                    return p_137812_.hasPermission(2);
                })
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("ticks", IntegerArgumentType.integer()).executes(css -> {
                            return timeWarp(css.getSource(), BlockPosArgument.getLoadedBlockPos(css, "pos"), IntegerArgumentType.getInteger(css, "ticks"));
                        }))));
    }

    private static int timeWarp(
            CommandSourceStack source,
            BlockPos target,
            Integer ticks
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tfbe)) {
            // TODO: Better error handling?
            return -1;
        }

        tfbe.warpTime(ticks);
        return 0;
    }
}
