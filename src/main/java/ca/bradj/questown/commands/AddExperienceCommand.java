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

public class AddExperienceCommand {
    public static void register(CommandDispatcher<CommandSourceStack> src) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> entitiesArg = Commands.argument(
                "entities",
                EntityArgument.entities()
        );
        RequiredArgumentBuilder<CommandSourceStack, Integer> amtArg = Commands.argument(
                "amount",
                IntegerArgumentType.integer()
        );

        LiteralArgumentBuilder<CommandSourceStack> experienceSubCmd = Commands.literal("experience");
        LiteralArgumentBuilder<CommandSourceStack> experienceAddSubCmd = Commands.literal("add");

        // @formatter:off
        src.register(
                Commands.literal("qt").then(
                        experienceSubCmd.then(
                                experienceAddSubCmd
                                        .requires(AddExperienceCommand::isCreative)
                                        .then(entitiesArg
                                        .then(amtArg
                                        .executes(css -> addExperience(
                                                EntityArgument.getEntities(css, "entities"),
                                                IntegerArgumentType.getInteger(css, "amount")
                                        ))))
                        )
                )
        );
        // @formatter:on
    }

    private static boolean isCreative(CommandSourceStack p_137812_) {
        return p_137812_.hasPermission(2);
    }

    private static int addExperience(
            Collection<? extends Entity> targets,
            int amount
    ) {
        for (Entity e : targets) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            TownInterface town = vme.getTown();
            town.getVillagerHandle().addExperience(vme.getUUID(), amount);
        }
        return 0;
    }
}
