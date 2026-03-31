package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.world.QTWorldAccess;

import java.util.function.Supplier;

public record BeforeInitEvent(
        Supplier<QTWorldAccess> world,
        ItemCheckReplacer<MCHeldItem> replaceIngredients,
        ItemCheckReplacer<MCTownItem> replaceTools,
        JobCheckReplacer jobBlockCheckReplacer,
        SupplyRoomCheckReplacer supplyRoomCheckReplacer
) {
}
