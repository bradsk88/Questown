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

/** Finds a worldgen flag's rotation on its first tick, from which of four positions holds the campfire. Gives up after {@value #MAX_RETRY_TICKS} ticks with no match; two matches end the lessons at once. */
public final class HelperChickenRotationDetector {

    private static final int MAX_RETRY_TICKS = 20;

    private static final Map<BlockPos, Integer> RETRY_COUNTERS = new HashMap<>();

    private HelperChickenRotationDetector() {
    }

    /** For autotests: clears the retry counts between scenarios. */
    public static void clearRetryCounters() {
        RETRY_COUNTERS.clear();
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
            finalizeDetection(flag, Rotation.NONE, true);
            return;
        }

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

    /** The rotation of a candidate position that holds a campfire, or NONE. Never ends the lessons or saves anything. */
    public static Rotation detectRotationOnce(TownFlagBlockEntity flag) {
        ServerLevel sl = flag.getServerLevel();
        if (sl == null) {
            return Rotation.NONE;
        }
        BlockPos flagPos = flag.getTownFlagBasePos();
        for (Map.Entry<Rotation, BlockPos> e : candidatePositions(flagPos).entrySet()) {
            if (sl.getBlockState(e.getValue()).is(Blocks.CAMPFIRE)) {
                return e.getKey();
            }
        }
        return Rotation.NONE;
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
