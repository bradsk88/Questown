package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.ImmutableSnapshot;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.workstatus.State;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Command to show warp-relevant status information for debugging.
 * Usage: /qt debug warp-status <pos>
 */
public class WarpStatusCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, Coordinates> posArg = Commands.argument(
                "pos",
                BlockPosArgument.blockPos()
        );

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                Commands.literal("debug").then(
                    Commands.literal("warp-status")
                        .requires(AddExperienceCommand::isCreative)
                        .then(posArg
                        .executes(css -> run(
                            css.getSource(),
                            BlockPosArgument.getLoadedBlockPos(css, "pos")
                        )))
                )
            )
        );
        // @formatter:on
    }

    static int run(
            CommandSourceStack source,
            BlockPos target
    ) {
        @Nullable TownFlagBlockEntity tfbe = QTCommands.getFlagOrBroadcast(source, target);
        if (tfbe == null) {
            return -1;
        }

        QT.FLAG_LOGGER.info("=== WARP STATUS ===");
        QT.FLAG_LOGGER.info("Town Flag: {}", target);

        // Villager states
        QT.FLAG_LOGGER.info("--- Villagers ---");
        int villagerIdx = 0;
        for (LivingEntity entity : tfbe.getVillagerHandle().entities()) {
            if (entity instanceof VisitorMobEntity v) {
                ImmutableSnapshot<MCHeldItem, ?> snapshot = v.getJobJournalSnapshot();
                QT.FLAG_LOGGER.info(
                        "[{}] UUID: {} Job: {} Inventory: {}",
                        villagerIdx,
                        v.getUUID().toString().substring(0, 8),
                        snapshot.jobId(),
                        snapshot.items()
                );
                villagerIdx++;
            }
        }

        // Work block states
        QT.FLAG_LOGGER.info("--- Work Block States ---");
        Map<BlockPos, State> workStates = tfbe.getWorkStatusHandle(null).getAll();
        for (Map.Entry<BlockPos, State> entry : workStates.entrySet()) {
            QT.FLAG_LOGGER.info(
                    "  {}: {}",
                    entry.getKey(),
                    entry.getValue().toShortString()
            );
        }

        // Container contents summary
        QT.FLAG_LOGGER.info("--- Containers ---");
        TownContainers.findAllContainersMatching(tfbe, item -> true)
                .limit(10)
                .forEach(container -> QT.FLAG_LOGGER.info(
                        "  {}: {} items",
                        container.getPosition(),
                        countItems(container)
                ));

        QT.FLAG_LOGGER.info("===================");
        return 0;
    }

    private static int countItems(ContainerTarget<?, ?> container) {
        int count = 0;
        for (int i = 0; i < container.getContainer().size(); i++) {
            if (!container.getContainer().getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
