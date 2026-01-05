package ca.bradj.questown.jobs;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public record Inpoots<TOWN>(
        TOWN town,
        ServerLevel level,
        UUID villagerUUID
) {
}
