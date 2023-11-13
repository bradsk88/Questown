package ca.bradj.questown.blocks;

import net.minecraft.server.level.ServerPlayer;

public interface OpenMenuListener {
    void openMenuRequested(ServerPlayer sp);
}
