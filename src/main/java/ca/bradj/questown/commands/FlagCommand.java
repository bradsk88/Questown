package ca.bradj.questown.commands;

import ca.bradj.questown.core.advancements.ApproachTownTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

public class FlagCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("flag");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("place_above");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd
                    .requires(AddExperienceCommand::isCreative)
                    .then(posArg
                        .then(subSubCmd
                            .executes(css -> setBlock(
                                css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos")
                            )))
                )
            )
        );
        // @formatter:on
    }

    private static int setBlock(
            CommandSourceStack source,
            BlockPos target
    ) {
        BlockEntity e = source.getLevel().getBlockEntity(target);
        if ((e instanceof TownFlagBlockEntity)) {
            return 0;
        }

        BlockPos flagPos = target.above();
        source.getLevel().setBlockAndUpdate(flagPos, BlocksInit.COBBLESTONE_TOWN_FLAG.get().defaultBlockState());
        markCommandPlacedFlagAsChickenIneligible(source, flagPos);

        AdvancementsInit.APPROACH_TOWN_TRIGGER.trigger(
                source.getPlayer(), ApproachTownTrigger.Triggers.FirstVisit
        );
        return 0;
    }

    // Command-placed flags skip the helper-chicken arc entirely: only worldgen-placed
    // flags satisfy the arc's scaffolding assumptions. The BE's onLoad already ran
    // initializeFreshFlag(false) which sets the chicken bits to their defaults via
    // each InitPair's onFlagPlace consumer. Set our bits AFTER that runs, so the
    // command's intent survives the InitPair defaults.
    private static void markCommandPlacedFlagAsChickenIneligible(
            CommandSourceStack source,
            BlockPos flagPos
    ) {
        if (!(source.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
            return;
        }
        flag.setChickenEverSpawned(true);
        flag.setChickenRotationDetected(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }
}
