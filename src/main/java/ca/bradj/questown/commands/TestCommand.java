package ca.bradj.questown.commands;

import ca.bradj.questown.commands.test.TestBlueprint;
import ca.bradj.questown.commands.test.TestBlueprintRegistry;
import ca.bradj.questown.commands.test.TestBlueprintRegistry;
import ca.bradj.questown.commands.test.TestExecutor;
import ca.bradj.questown.commands.test.PlayerTestOutput;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.loader.neo.TestTickListener;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

public class TestCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> src,
            CommandBuildContext ctx
    ) {
        RequiredArgumentBuilder<CommandSourceStack, JobID> jobArg = Commands.argument(
                "job_id", JobArgument.job(ctx)
        );
        RequiredArgumentBuilder<CommandSourceStack, Integer> warpArg = Commands.argument(
                "warp_amount", IntegerArgumentType.integer(1)
        );

        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("test")
                    .requires(AddExperienceCommand::isCreative)
                    .then(Commands.literal("list")
                        .executes(css -> listScenarios(css.getSource()))
                    )
                    .then(jobArg
                    .then(warpArg
                        .executes(css -> warn(css.getSource()))
                        .then(Commands.literal("destroy")
                            .executes(css -> run(
                                css.getSource(),
                                JobArgument.getJob(css, "job_id"),
                                IntegerArgumentType.getInteger(css, "warp_amount")
                            ))
                        )
                    ))
            )
        );
        // @formatter:on
    }

    private static int listScenarios(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return -1;
        }
        List<String> names = TestBlueprintRegistry.allScenarioNames();
        Compat.sendMessage(player, Component.literal(
                "[qt test] " + names.size()
                        + " scenario(s) for -Dquestown.autotest.only=<name> (substring match):")
        );
        for (String name : names) {
            Compat.sendMessage(player, Component.literal("  " + name));
        }
        return 1;
    }

    private static int warn(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Compat.sendMessage(player, Component.literal(
                    "[qt test] This will destroy a 15x15 area near you. Add 'destroy' to confirm."
            ));
        } catch (Exception e) {
            return -1;
        }
        return 0;
    }

    private static int run(CommandSourceStack source, JobID jobId, int warpAmount) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return -1;
        }

        TestBlueprint blueprint = TestBlueprintRegistry.get(jobId);
        if (blueprint == null) {
            Compat.sendMessage(player, Component.literal(
                    "[qt test] No tests available for job: " + jobId.rootId() + ":" + jobId.jobId()
            ));
            return 0;
        }

        ServerLevel level = source.getLevel();
        BlockPos origin = new BlockPos(player.blockPosition());
        PlayerTestOutput output = new PlayerTestOutput(player, "_qtdev test");

        TestExecutor executor = new TestExecutor(level, output, origin, jobId, warpAmount, blueprint);

        TestTickListener listener = new TestTickListener(executor);
        Loader.registerCommonHandlers(listener);

        Compat.sendMessage(player, Component.literal(
                "[qt test] Starting test for " + jobId.rootId() + ":" + jobId.jobId() +
                        " with warp=" + warpAmount
        ));

        return 1;
    }
}
