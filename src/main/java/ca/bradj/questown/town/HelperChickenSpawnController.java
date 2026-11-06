package ca.bradj.questown.town;

import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;

/** Spawns the helper chicken next to the flag once: after rotation detection, unless the lessons ended, with a player within about 10 blocks. */
public final class HelperChickenSpawnController {

    private static final double NEAR_PLAYER_RADIUS = 10.0D;
    private static final double NEAR_PLAYER_RADIUS_SQR = NEAR_PLAYER_RADIUS * NEAR_PLAYER_RADIUS;

    private HelperChickenSpawnController() {
    }

    public static void tick(TownFlagBlockEntity flag) {
        if (flag.getChickenEverSpawned()) {
            return;
        }
        if (flag.getChickenArcForfeit()) {
            return;
        }
        if (!flag.getChickenRotationDetected()) {
            return;
        }
        ServerLevel sl = flag.getServerLevel();
        if (sl == null) {
            return;
        }
        BlockPos flagPos = flag.getTownFlagBasePos();
        if (!isPlayerNear(sl, flagPos)) {
            return;
        }
        spawn(sl, flag, flagPos);
    }

    private static boolean isPlayerNear(
            ServerLevel sl,
            BlockPos flagPos
    ) {
        Player nearest = sl.getNearestPlayer(
                flagPos.getX() + 0.5D,
                flagPos.getY() + 0.5D,
                flagPos.getZ() + 0.5D,
                NEAR_PLAYER_RADIUS,
                false
        );
        if (nearest == null) {
            return false;
        }
        double dx = nearest.getX() - (flagPos.getX() + 0.5D);
        double dy = nearest.getY() - (flagPos.getY() + 0.5D);
        double dz = nearest.getZ() - (flagPos.getZ() + 0.5D);
        return (dx * dx + dy * dy + dz * dz) <= NEAR_PLAYER_RADIUS_SQR;
    }

    /** Spawns the helper chicken for a hand-placed flag, skipping rotation detection. Returns whether it spawned. */
    public static boolean forceSpawn(TownFlagBlockEntity flag) {
        ServerLevel sl = flag.getServerLevel();
        if (sl == null) {
            return false;
        }
        flag.setChickenArcForfeit(false);
        flag.setChickenStructureRotation(HelperChickenRotationDetector.detectRotationOnce(flag));
        flag.setChickenRotationDetected(true);
        spawn(sl, flag, flag.getTownFlagBasePos());
        return flag.getChickenEverSpawned();
    }

    private static void spawn(
            ServerLevel sl,
            TownFlagBlockEntity flag,
            BlockPos flagPos
    ) {
        HelperChickenEntity chicken = EntitiesInit.HELPER_CHICKEN.get().create(sl);
        if (chicken == null) {
            return;
        }
        BlockPos spawnPos = findSpawnPos(sl, flagPos);
        chicken.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                sl.getRandom().nextFloat() * 360.0F,
                0.0F
        );
        chicken.setOwnerFlagPos(flagPos);
        chicken.finalizeSpawn(sl, sl.getCurrentDifficultyAt(spawnPos), MobSpawnType.EVENT, null, null);
        sl.addFreshEntity(chicken);

        flag.setChickenEverSpawned(true);
        CompoundTag tag = Compat.getBlockStoredTagData(flag);
        flag.writeTownData(tag);
        flag.setChanged();
    }

    private static BlockPos findSpawnPos(
            ServerLevel sl,
            BlockPos flagPos
    ) {
        BlockPos[] candidates = new BlockPos[]{
                flagPos.north(2),
                flagPos.east(2),
                flagPos.south(2),
                flagPos.west(2)
        };
        for (BlockPos candidate : candidates) {
            if (sl.getBlockState(candidate).isAir()
                    && sl.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return flagPos.above();
    }
}
