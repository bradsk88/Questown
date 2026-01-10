package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Commands to quickly set up test scenarios for warp testing.
 * Usage:
 *   /qt test fill-supplies <pos> - Fill containers with common supplies
 *   /qt test clear-containers <pos> - Empty all containers
 */
public class WarpTestSetupCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                Commands.literal("test").then(
                    Commands.literal("fill-supplies")
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .executes(css -> fillSupplies(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                ).then(
                    Commands.literal("clear-containers")
                        .requires(AddExperienceCommand::isCreative)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(css -> clearContainers(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                )
            )
        );
        // @formatter:on
    }

    private static int fillSupplies(
            CommandSourceStack source,
            BlockPos target
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        AtomicInteger filledCount = new AtomicInteger(0);

        // Fill containers with common test supplies
        TownContainers.findAllContainersMatching(tfbe, item -> true)
                .limit(5)
                .forEach(container -> {
                    // Add carrots (common gatherer supply)
                    addItemToContainer(container, new ItemStack(Items.CARROT, 32));
                    // Add wheat (baker supply)
                    addItemToContainer(container, new ItemStack(Items.WHEAT, 32));
                    // Add sticks (common crafting material)
                    addItemToContainer(container, new ItemStack(Items.STICK, 32));
                    filledCount.incrementAndGet();
                });

        QT.FLAG_LOGGER.info("Filled {} containers with test supplies", filledCount.get());
        return 0;
    }

    private static void addItemToContainer(ContainerTarget<?, MCTownItem> container, ItemStack stack) {
        for (int i = 0; i < container.size(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, MCTownItem.fromMCItemStack(stack));
                return;
            }
        }
    }

    private static int clearContainers(
            CommandSourceStack source,
            BlockPos target
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        AtomicInteger clearedCount = new AtomicInteger(0);

        TownContainers.findAllContainersMatching(tfbe, item -> true)
                .forEach(container -> {
                    for (int i = 0; i < container.size(); i++) {
                        container.setItem(i, MCTownItem.Air());
                    }
                    clearedCount.incrementAndGet();
                });

        QT.FLAG_LOGGER.info("Cleared {} containers", clearedCount.get());
        return 0;
    }
}
