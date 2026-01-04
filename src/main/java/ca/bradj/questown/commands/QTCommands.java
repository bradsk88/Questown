package ca.bradj.questown.commands;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public class QTCommands {
    public static TownFlagBlockEntity getFlagOrBroadcast(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity tfbe)) {
            if (!(source.getEntity() instanceof ServerPlayer sp)) {
                return null;
            }
            Compat.sendMessage(
                    sp,
                    Compat.translatable("messages.command.failed_not_flag", Util.getShortString(target))
            );
            return null;
        }
        return tfbe;
    }
}
