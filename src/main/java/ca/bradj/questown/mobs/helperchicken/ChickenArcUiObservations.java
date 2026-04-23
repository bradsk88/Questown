package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-side fan-in for UI-open events that drive the helper-chicken arc
 * (U6). Called from the villager and flag UI open paths — each invocation
 * sets a transient observation flag on the matching flag BE; the
 * {@link ChickenArcController} tick consumes and clears it.
 *
 * <p>UI open paths don't fire often enough to warrant per-player tracking;
 * the chicken arc only cares that the current player encountered the menu
 * at all. Multiplayer co-op accepts either player's action.
 */
public final class ChickenArcUiObservations {

    private ChickenArcUiObservations() {
    }

    public static void markVillagerUiOpened(ServerLevel level, BlockPos flagPos) {
        withFlag(level, flagPos, flag -> flag.setChickenObservedVillagerUiOpen(true));
    }

    public static void markFlagUiOpened(net.minecraft.world.level.Level level, BlockPos flagPos) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        withFlag(sl, flagPos, flag -> flag.setChickenObservedFlagUiOpen(true));
    }

    private static void withFlag(
            ServerLevel level,
            BlockPos flagPos,
            java.util.function.Consumer<TownFlagBlockEntity> action
    ) {
        if (level == null || flagPos == null) {
            return;
        }
        if (level.getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
            action.accept(flag);
        }
    }
}
