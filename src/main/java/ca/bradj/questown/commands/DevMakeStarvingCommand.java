package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class DevMakeStarvingCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> entitiesArg = Commands.argument(
                "entities",
                EntityArgument.entities()
        );

        LiteralArgumentBuilder<CommandSourceStack> subCmd = Commands.literal("villagers");
        LiteralArgumentBuilder<CommandSourceStack> subSubCmd = Commands.literal("effects");
        LiteralArgumentBuilder<CommandSourceStack> subSubSubCmd = Commands.literal("make_starving");

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                subCmd.then(
                    subSubCmd.then(
                        subSubSubCmd
                            .requires(AddExperienceCommand::isCreative)
                            .then(entitiesArg
                                .executes(css -> run(
                                        EntityArgument.getEntities(css, "entities")
                                )))
                )
            )
        ));
        // @formatter:on
    }

    private static int run(
            Collection<? extends Entity> targets
    ) {

        for (Entity e : targets) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            TownInterface town = vme.getTown();
            town.getVillagerHandle().setStarving(vme.getVUID(), true);
        }
        return 0;
    }
}
