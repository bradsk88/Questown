package ca.bradj.questown.commands;

import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class SetJobCommand {
    public static void register(
            CommandDispatcher<CommandSourceStack> src,
            CommandBuildContext ctx
    ) {
        RequiredArgumentBuilder<CommandSourceStack, EntitySelector> entitiesArg = Commands.argument(
                "entities",
                EntityArgument.entities()
        );
        RequiredArgumentBuilder<CommandSourceStack, JobID> amtArg = Commands.argument("job_id", JobArgument.job(ctx));

        LiteralArgumentBuilder<CommandSourceStack> cmdVillagers = Commands.literal("villagers");
        LiteralArgumentBuilder<CommandSourceStack> cmdVillagersJobs = Commands.literal("jobs");
        LiteralArgumentBuilder<CommandSourceStack> cmdVillagersJobsSet = Commands.literal("set");

        // @formatter:off
        src.register(
            Commands.literal("qt").then(
                cmdVillagers.then(
                cmdVillagersJobs.then(
                cmdVillagersJobsSet
                    .requires(AddExperienceCommand::isCreative)
                    .then(entitiesArg
                    .then(amtArg
                    .executes(css -> setJob(
                        EntityArgument.getEntities(css, "entities"),
                        JobArgument.getJob(css, "job_id")
                    ))))
                )
            )
        ));


        // Also expose it via the jobs tree
        cmdVillagers = Commands.literal("jobs");
        cmdVillagersJobs = Commands.literal("set");
        src.register(
            Commands.literal("qt").then(
                cmdVillagers.then(
                cmdVillagersJobs
                    .requires(AddExperienceCommand::isCreative)
                    .then(entitiesArg
                    .then(amtArg
                    .executes(css -> setJob(
                        EntityArgument.getEntities(css, "entities"),
                        JobArgument.getJob(css, "job_id")
                    ))))
            )
        ));
        // @formatter:on
    }

    private static int setJob(
            Collection<? extends Entity> targets,
            JobID job
    ) {
        for (Entity e : targets) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            // The owner makes the worker: a hand-rolled change + single unlock made a cook with
            // no cook/fuel, unlike any player-made one (r15).
            VillagerUUID vuid = vme.getVUID();
            if (vuid == null) {
                continue;
            }
            vme.getTown().getVillagerHandle().assignJob(vuid, job);
        }
        return 0;
    }
}
