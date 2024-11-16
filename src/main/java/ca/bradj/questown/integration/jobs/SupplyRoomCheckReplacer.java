package ca.bradj.questown.integration.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.serialization.MCRoom;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SupplyRoomCheckReplacer {
    private SupplyRoomCheck inner = (mcHeldItems, mcRoomRoomRecipeMatch) -> true;

    public SupplyRoomCheckReplacer() {
    }

    public void accept(Function<SupplyRoomCheck, SupplyRoomCheck> replacer) {
        this.inner = replacer.apply(inner);
    }



    public static Predicate<RoomRecipeMatch<MCRoom>> withItems(
            SupplyRoomCheckReplacer srcr,
            Supplier<? extends Collection<MCHeldItem>> items
    ) {
        return r -> srcr.inner.test(items.get(), r);
    }
}
