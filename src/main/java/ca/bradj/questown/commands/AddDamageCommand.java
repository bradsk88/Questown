package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class AddDamageCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qt_damage_add")
                                   .requires((p_137812_) -> p_137812_.hasPermission(2))
                                   .then(Commands.argument("entities", EntityArgument.entities())
                                                 .then(Commands.argument("ticks", IntegerArgumentType.integer())
                                                               .executes(css -> addDamage(
                                                                       EntityArgument.getEntities(css, "entities"),
                                                                       IntegerArgumentType.getInteger(css, "ticks")
                                                               )))));
    }

    private static int addDamage(
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
