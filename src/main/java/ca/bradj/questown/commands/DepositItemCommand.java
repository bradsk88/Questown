package ca.bradj.questown.commands;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

// Dev-only: the agent-run village test has no connected mouse/keyboard, so it
// cannot open a chest and drop an item the way a player would. This command is
// that action, server-side: it puts an item into a registered town container,
// the same path a player depositing into a chest takes (the executor's
// deposit()). A player leaving food in the storeroom clears a UNMET_ITEM need;
// a player leaving a quest item in a container completes an item quest. No
// permission gate, so it runs from the console (FIFO).
public class DepositItemCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src, CommandBuildContext ctx) {
        // ItemArgument (not StringArgumentType.word()): the FIFO/console parses
        // ItemArgument correctly (vanilla `give` works from the FIFO) but word()
        // rejects the item token as "trailing data". ItemArgument resolves the
        // item from the registry, matching the executor's deposit().
        RequiredArgumentBuilder<CommandSourceStack, ItemInput> item =
                Commands.argument("item", ItemArgument.item(ctx));
        RequiredArgumentBuilder<CommandSourceStack, Integer> count =
                Commands.argument("count", IntegerArgumentType.integer());
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("deposit")
                    .then(item.then(count
                        .executes(css -> deposit(
                                css.getSource(),
                                ItemArgument.getItem(css, "item").getItem(),
                                IntegerArgumentType.getInteger(css, "count")
                            ))))
            )
        );
    }

    private static int deposit(CommandSourceStack source, net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (stack.isEmpty()) {
            QT.FLAG_LOGGER.warn("[deposit] unknown item {}", item);
            return 1;
        }
        boolean any = false;
        for (TownFlagBlockEntity flag : ca.bradj.questown.town.entity.TownFlags.all()) {
            for (ContainerTarget<?, MCTownItem> target :
                    TownContainers.getAllContainers(flag, source.getLevel())) {
                if (source.getLevel().getBlockEntity(target.getBlockPos()) instanceof Container c) {
                    for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                        if (c.getItem(i).isEmpty()) {
                            c.setItem(i, stack.copy());
                            stack.setCount(0);
                        }
                    }
                    c.setChanged();
                    any = true;
                }
            }
        }
        if (!any) {
            QT.FLAG_LOGGER.warn("[deposit] no registered container for the flag");
            return 1;
        }
        QT.FLAG_LOGGER.info("[deposit] deposited {} into a registered container", item);
        return 0;
    }
}
