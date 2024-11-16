package ca.bradj.questown.commands;

import ca.bradj.questown.core.network.OpenMultiVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.town.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class OpenVillagersMenuCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qt_villagers_menu_all_open")
                                   .requires((p_137812_) -> {
                                       return p_137812_.hasPermission(2);
                                   })
                                   .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(css -> {
                                       return run(css.getSource(), BlockPosArgument.getLoadedBlockPos(css, "pos"));
                                   })));
    }

    private static int run(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if (!(e instanceof TownFlagBlockEntity)) {
            // TODO: Better error handling?
            return -1;
        }

        QuestownNetwork.CHANNEL.sendToServer(new OpenMultiVillagerMenuMessage(
                target.getX(),
                target.getY(),
                target.getZ()
        ));
        return 0;
    }
}
