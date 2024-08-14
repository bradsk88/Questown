package ca.bradj.questown.commands;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class AddDamageCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qtdamageadd")
                                   .requires((p_137812_) -> p_137812_.hasPermission(2))
                                   .then(Commands.argument("targets", EntityArgument.entities()).executes(css -> addDamage(
                                           css.getSource(),
                                           EntityArgument.getEntities(css, "targets")
                                   ))));
    }

    private static int addDamage(
            CommandSourceStack source,
            Collection<? extends Entity> targets
    ) {
        for (Entity e : targets) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            vme.setArrowCount(vme.getArrowCount() + 1);
            TownInterface town = vme.getTown();
            town.getVillagerHandle().addDamage(vme.getUUID());
        }
        return 0;
    }
}
