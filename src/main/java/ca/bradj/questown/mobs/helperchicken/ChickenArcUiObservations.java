package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Records that a villager or flag menu was opened, for the helper chicken's lessons. Either player counts. */
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
