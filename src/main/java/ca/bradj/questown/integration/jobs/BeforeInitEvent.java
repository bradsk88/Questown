package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Supplier;

public record BeforeInitEvent(
        Supplier<ServerLevel> level,
        ItemCheckReplacer<MCHeldItem> replaceIngredients,
        ItemCheckReplacer<MCTownItem> replaceTools,
        JobCheckReplacer jobBlockCheckReplacer,
        SupplyRoomCheckReplacer supplyRoomCheckReplacer
) {
}
