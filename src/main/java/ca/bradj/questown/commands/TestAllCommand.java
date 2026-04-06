package ca.bradj.questown.commands;

import ca.bradj.questown.commands.test.TestAllExecutor;
import ca.bradj.questown.commands.test.PlayerTestOutput;
import ca.bradj.questown.mc.Compat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

public class TestAllCommand {

    public static void register(
            CommandDispatcher<CommandSourceStack> src,
            CommandBuildContext ctx
    ) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("testall")
                    .requires(AddExperienceCommand::isCreative)
                    .then(Commands.argument("warp_amount", IntegerArgumentType.integer(1))
                        .executes(css -> warn(css.getSource()))
                        .then(Commands.literal("destroy")
                            .executes(css -> run(
                                css.getSource(),
                                null,
                                IntegerArgumentType.getInteger(css, "warp_amount")
                            ))
                        )
                    )
                    .then(Commands.argument("category", StringArgumentType.word())
                        .then(Commands.argument("warp_amount", IntegerArgumentType.integer(1))
                            .executes(css -> warn(css.getSource()))
                            .then(Commands.literal("destroy")
                                .executes(css -> run(
                                    css.getSource(),
                                    StringArgumentType.getString(css, "category"),
                                    IntegerArgumentType.getInteger(css, "warp_amount")
                                ))
                            )
                        )
                    )
            )
        );
        // @formatter:on
    }

    private static int warn(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Compat.sendMessage(player, Component.literal(
                    "[_qtdev testall] This will destroy a 15x15 area near you. Add 'destroy' to confirm."
            ));
        } catch (Exception e) {
            return -1;
        }
        return 0;
    }

    private static int run(CommandSourceStack source, @Nullable String category, int warpAmount) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return -1;
        }

        ServerLevel level = source.getLevel();
        BlockPos origin = new BlockPos(player.blockPosition());
        PlayerTestOutput output = new PlayerTestOutput(player, "_qtdev testall");

        TestAllExecutor executor = new TestAllExecutor(level, output, origin, warpAmount, category);

        TestAllTickListener listener = new TestAllTickListener(executor);
        MinecraftForge.EVENT_BUS.register(listener);

        String scope = category != null ? category : "all";
        Compat.sendMessage(player, Component.literal(
                "[_qtdev testall] Starting " + scope + " tests with warp=" + warpAmount
        ));

        return 1;
    }

    static class TestAllTickListener {
        private final TestAllExecutor executor;

        TestAllTickListener(TestAllExecutor executor) {
            this.executor = executor;
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (executor.tick()) {
                MinecraftForge.EVENT_BUS.unregister(this);
            }
        }
    }
}
