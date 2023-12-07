package ca.bradj.questown.commands;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class JobArgument implements ArgumentType<JobID> {
    public static @NotNull ArgumentType<JobID> job() {
        return new JobArgument();
    }

    public static JobID getJob(
            CommandContext<CommandSourceStack> ctx, String name
    ) {
        return ctx.getArgument(name, JobID.class);
    }

    @Override
    public JobID parse(StringReader reader) throws CommandSyntaxException {
        String rootId = reader.readString(); // "Consume" job category from the reader
        reader.read(); // Consume colon from the reader
        String workId = reader.readString(); // "Consume" specific job from the reader
        return new JobID(rootId, workId);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder
    ) {
        JobsRegistry.getAllJobs().forEach(
                v -> builder.suggest(String.format("%s:%s", v.rootId(), v.jobId()))
        );
        return builder.buildFuture();
    }
}
