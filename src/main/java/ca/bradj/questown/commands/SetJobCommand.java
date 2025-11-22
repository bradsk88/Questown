package ca.bradj.questown.commands;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
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
            TownInterface town = vme.getTown();
            town.getVillagerHandle().changeJobForVillager(vme.getUUID(), job, false);
            town.getVillagerHandle().unlockJob(vme.getUUID(), job);
        }
        return 0;
    }
}
