package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.Channel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/** {@link Channel.Context} over NeoForge's {@link IPayloadContext}. */
final class NeoPayloadContext implements Channel.Context {

    private final IPayloadContext delegate;

    NeoPayloadContext(IPayloadContext delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        return delegate.enqueueWork(work);
    }

    @Override
    public @Nullable Player sender() {
        return delegate.player();
    }
}
