package ca.bradj.questown.commands;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class SetJobCommand {
    public static void register(CommandDispatcher<CommandSourceStack> p_137808_) {
        p_137808_.register(Commands.literal("qtsetjob")
                .requires((p_137812_) -> {
                    return p_137812_.hasPermission(2);
                })
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("job_id", JobArgument.job()).executes(css -> {
                            return setJob(css.getSource(), EntityArgument.getEntities(css, "targets"), JobArgument.getJob(css, "job_id"));
                        }))));
    }

    private static int setJob(
            CommandSourceStack source,
            Collection<? extends Entity> targets,
            JobID job
    ) {
        for (Entity e : targets) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            TownInterface town = vme.getTown();
            town.changeJobForVisitor(vme.getUUID(), job);
        }
        return 0;
    }
}
