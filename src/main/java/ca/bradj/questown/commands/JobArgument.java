package ca.bradj.questown.commands;

import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.Works;
import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentSerializer;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class JobArgument implements ArgumentType<JobID> {
    private Collection<JobID> jobIDs;

    public JobArgument() {
        jobIDs = Works.ids();
    }

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
        jobIDs.forEach(
                v -> builder.suggest(String.format("%s:%s", v.rootId(), v.jobId()))
        );
        return builder.buildFuture();
    }

    public static class Serializer implements ArgumentSerializer<JobArgument> {
        @Override
        public void serializeToNetwork(JobArgument p_235375_, FriendlyByteBuf p_235376_) {
            p_235376_.writeCollection(p_235375_.jobIDs, (buf, j) -> {
                buf.writeUtf(j.rootId());
                buf.writeUtf(j.jobId());
            });
        }

        @Override
        public JobArgument deserializeFromNetwork(FriendlyByteBuf p_235377_) {
            JobArgument jobArgument = new JobArgument();
            jobArgument.jobIDs = p_235377_.readCollection(
                    ArrayList::new,
                    buf -> new JobID(buf.readUtf(), buf.readUtf())
            );
            return jobArgument;
        }

        @Override
        public void serializeToJson(JobArgument p_235373_, JsonObject p_235374_) {
        }
    }
}
