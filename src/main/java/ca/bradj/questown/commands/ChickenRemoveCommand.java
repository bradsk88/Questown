package ca.bradj.questown.commands;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** {@code /questown chicken remove <pos>}: ends the helper chicken's lessons for that flag and removes the chicken. Creative only. */
public class ChickenRemoveCommand {

    private static final double CHICKEN_SEARCH_RANGE = 64.0D;

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("questown").then(
                Commands.literal("chicken")
                    .requires(AddExperienceCommand::isCreative)
                    .then(Commands.literal("remove").then(
                        posArg.executes(css -> remove(
                                css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                        ))
                    ))
            )
        );
        // @formatter:on
    }

    private static int remove(
            CommandSourceStack source,
            BlockPos target
    ) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockEntity(target) instanceof TownFlagBlockEntity flag)) {
            source.sendFailure(Compat.translatable("messages.chicken.remove.not_a_flag"));
            return 0;
        }
        flag.setChickenArcForfeit(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();

        int despawned = despawnChickensForFlag(level, flag);
        source.sendSuccess(Compat.translatable("messages.chicken.remove.done", despawned),
                true
        );
        return despawned;
    }

    private static int despawnChickensForFlag(
            ServerLevel level,
            TownFlagBlockEntity flag
    ) {
        BlockPos flagPos = flag.getTownFlagBasePos();
        AABB search = new AABB(flagPos).inflate(CHICKEN_SEARCH_RANGE);
        List<HelperChickenEntity> matches = level.getEntitiesOfClass(
                HelperChickenEntity.class,
                search,
                e -> flagPos.equals(e.getOwnerFlagPos())
        );
        matches.forEach(e -> e.discard());
        return matches.size();
    }
}
