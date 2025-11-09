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

    public static final String AWARENESS_COMPUTE;
    public static final String JOB_POSSIBILITIES_COMPUTE;
    public static final String QUEST_BATCH_COMPUTE_NEXT;
    private static final ImmutableList<String> debugLogIds;

    static {
        ImmutableList.Builder<String> b = ImmutableList.builder();
        AWARENESS_COMPUTE = add(b, "awareness_compute");
        JOB_POSSIBILITIES_COMPUTE = add(b, "job_possibilities_compute");
        QUEST_BATCH_COMPUTE_NEXT = add(b, "quest_batch_compute_next");
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
