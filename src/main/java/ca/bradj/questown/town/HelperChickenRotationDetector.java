package ca.bradj.questown.town;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.helperchicken.HelperChickenBeatOffsets;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * First-tick rotation detection for worldgen-placed flags (U3).
 *
 * <p>On the flag BE's first tick (before {@link HelperChickenSpawnController}),
 * scans the four candidate anchor positions — one per {@link Rotation} — for
 * the authored anchor block (unlit or lit campfire at
 * {@link HelperChickenBeatOffsets#CAMPFIRE_OFFSET}). The unique match's rotation
 * is persisted on the flag BE.
 *
 * <p>Retry budget: up to {@value #MAX_RETRY_TICKS} ticks of zero-match scans are
 * allowed (anchor chunk may not be loaded yet). After the budget, the arc is
 * force-forfeited. Multiple matches are a hard forfeit with no retry — anchor
 * ambiguity is never recoverable.
 *
 * <p>Retry counter lives in a static {@link Map} keyed by flag {@link BlockPos}.
 * The counter is transient — it does not survive a server restart, which is
 * fine because a restart re-runs the scan with a fresh budget anyway.
 */
public final class HelperChickenRotationDetector {

    private static final int MAX_RETRY_TICKS = 20;

    // Transient per-flag retry counter. Cleared implicitly when the flag finalizes detection.
    private static final Map<BlockPos, Integer> RETRY_COUNTERS = new HashMap<>();

    private HelperChickenRotationDetector() {
    }

    public static void detectIfNeeded(TownFlagBlockEntity flag) {
        if (flag.getChickenRotationDetected()) {
            return;
        }
        ServerLevel sl = flag.getServerLevel();
        if (sl == null) {
            return;
        }

        BlockPos flagPos = flag.getTownFlagBasePos();
        Map<Rotation, BlockPos> candidates = candidatePositions(flagPos);
        EnumMap<Rotation, Boolean> matches = new EnumMap<>(Rotation.class);
        for (Map.Entry<Rotation, BlockPos> e : candidates.entrySet()) {
            boolean match = sl.getBlockState(e.getValue()).is(Blocks.CAMPFIRE);
            matches.put(e.getKey(), match);
        }

        long matchCount = matches.values().stream().filter(Boolean::booleanValue).count();
        if (matchCount == 1) {
            Rotation detected = matches.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(Rotation.NONE);
            finalizeDetection(flag, detected, false);
            return;
        }
        if (matchCount > 1) {
            // Ambiguity is an unrecoverable hard failure — no retry.
            finalizeDetection(flag, Rotation.NONE, true);
            return;
        }

        // Zero matches: retry up to MAX_RETRY_TICKS before forfeiting.
        int attempts = RETRY_COUNTERS.getOrDefault(flagPos, 0) + 1;
        if (attempts >= MAX_RETRY_TICKS) {
            finalizeDetection(flag, Rotation.NONE, true);
            return;
        }
        RETRY_COUNTERS.put(flagPos, attempts);
    }

    private static Map<Rotation, BlockPos> candidatePositions(BlockPos flagPos) {
        EnumMap<Rotation, BlockPos> out = new EnumMap<>(Rotation.class);
        for (Rotation r : Rotation.values()) {
            out.put(r, flagPos.offset(HelperChickenBeatOffsets.CAMPFIRE_OFFSET.rotate(r)));
        }
        return out;
    }

    private static void finalizeDetection(
            TownFlagBlockEntity flag,
            Rotation detected,
            boolean forfeit
    ) {
        flag.setChickenStructureRotation(detected);
        flag.setChickenRotationDetected(true);
        if (forfeit) {
            flag.setChickenArcForfeit(true);
        }
        RETRY_COUNTERS.remove(flag.getTownFlagBasePos());
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }
}
