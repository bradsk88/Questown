package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class AddDamageCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> entitiesArg = Commands.argument(
                "entities",
                EntityArgument.entities()
        );
        RequiredArgumentBuilder<CommandSourceStack, Integer> amtArg = Commands.argument(
                "amount",
                IntegerArgumentType.integer()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("villagers");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("damage");
        LiteralArgumentBuilder<CommandSourceStack> subSubSubCmd = Commands.literal("add");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                subCmd.then(
                    subSubCmd.then(
                        subSubSubCmd
                            .requires(AddExperienceCommand::isCreative)
                            .then(entitiesArg
                            .then(amtArg
                                .executes(css -> run(
                                        EntityArgument.getEntities(css, "entities"),
                                        IntegerArgumentType.getInteger(css, "amount")
                                ))))
                )
            )
        ));
        // @formatter:on
    }

    private static int run(
            Collection<? extends Entity> targets,
            int amount
    ) {

        for (Entity e : targets) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            vme.setArrowCount(vme.getArrowCount() + 1);
            TownInterface town = vme.getTown();
            for (int i = 0; i < amount; i++) {
                town.getVillagerHandle().addDamage(vme.getUUID());
            }
        }
        return 0;
    }
}
