package ca.bradj.questown.loader.neo;

import ca.bradj.questown.loader.Channel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * The neutral {@link Channel.Context} over NeoForge's {@link IPayloadContext}.
 * {@link #enqueueWork} is Neo's own main-thread scheduling (a no-op if already on it), and
 * {@link #sender()} is the player the packet was addressed to (or came from). NeoForge has no
 * "mark handled" concept: the registered handler owns the dispatch, so the former
 * {@code setPacketHandled(true)} is simply dropped from the neutral surface.
 */
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
