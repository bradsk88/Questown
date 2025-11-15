package ca.bradj.questown.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class DebugLogArgument implements ArgumentType<String> {

    private static final ImmutableList<String> debugLogIds;

    public static final String AWARENESS_COMPUTE;
    public static final String JOB_POSSIBILITIES_COMPUTE;
    public static final String QUEST_BATCH_COMPUTE_NEXT;
    public static final String TIME_WARP;
    public static final String TOWN_STATE_CHANGES;
    public static final String VILLAGER_NAVIGATION;
    public static final String JOB_LOGIC;
    public static final String SAMPLING;
    public static final String VILLAGER_STATS;

    static {
        ImmutableList.Builder<String> b = ImmutableList.builder();
        AWARENESS_COMPUTE = add(b, "awareness_compute");
        JOB_POSSIBILITIES_COMPUTE = add(b, "job_possibilities_compute");
        QUEST_BATCH_COMPUTE_NEXT = add(b, "quest_batch_compute_next");
        TIME_WARP = add(b, "time_warp");
        TOWN_STATE_CHANGES = add(b, "town_state_changes");
        VILLAGER_NAVIGATION = add(b, "villager_navigation");
        JOB_LOGIC = add(b, "job_logic");
        SAMPLING = add(b, "sampling");
        VILLAGER_STATS = add(b, "villager_stats");
        debugLogIds = b.build();
    }

    private static String add(
            ImmutableList.Builder<String> b,
            String val
    ) {
        b.add(val);
        return val;
    }


    public DebugLogArgument(CommandBuildContext ctx) {
    }

    public static @NotNull ArgumentType<String> debugLogs(CommandBuildContext ctx) {
        return new DebugLogArgument(ctx);
    }

    public static String getLog(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder
    ) {
        debugLogIds.forEach(builder::suggest);
        return builder.buildFuture();
    }
}
