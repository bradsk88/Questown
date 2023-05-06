package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class SpawnVisitorReward extends MCReward {

    public static final String ID = "spawn_visitor_reward";

    public SpawnVisitorReward(
            RewardType<? extends MCReward> rType,
            @NotNull TownInterface entity,
            @Nullable UUID visitorUUID
    ) {
        super(rType, () -> {
            while (!spawnVisitorNearby(entity, visitorUUID)) {
            } // FIXME: Instead of "while" loop, set state on entity and try adding during ticks
        });
    }

    public SpawnVisitorReward(TownInterface entity) {
        this(entity, null);
    }

    public SpawnVisitorReward(
            TownInterface entity,
            UUID visitorUUID
    ) {
        this(RewardsInit.VISITOR.get(), entity, visitorUUID);
    }

    private static boolean spawnVisitorNearby(
            TownInterface entity,
            @Nullable UUID visitorUUID
    ) {
        if (entity.getLevel() == null || !(entity.getLevel() instanceof ServerLevel sl)) {
            return false;
        }

        int radius = 30; // TODO: Add to config?
        Random random = sl.getRandom();

        BlockPos center = entity.getBlockPos();
        Optional<TownFlagBlockEntity> oEntity = sl.getBlockEntity(center, TilesInit.TOWN_FLAG.get());
        if (oEntity.isEmpty()) {
            return false;
        }
        TownFlagBlockEntity flagEnt = oEntity.get();

        // Get a random position within the specified radius around the center
        BlockPos pos = center.offset(random.nextInt(radius * 2) - radius, 0, random.nextInt(radius * 2) - radius);

        // Find the top block at the random position
        int surfaceY = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        BlockPos surfacePos = pos.atY(surfaceY);
        BlockState surfaceBlockState = sl.getBlockState(surfacePos);

        // Check if the top block is a solid block, such as grass or dirt
        if (surfaceBlockState.getMaterial().isSolid()) {
            // Spawn the entity on top of the solid block
            VisitorMobEntity vEntity = new VisitorMobEntity(EntitiesInit.VISITOR.get(), sl, flagEnt);
            vEntity.moveTo(
                    surfacePos.getX() + 0.5,
                    surfacePos.getY() + 1,
                    surfacePos.getZ() + 0.5,
                    random.nextFloat() * 360,
                    0
            );
            if (visitorUUID != null) {
                vEntity.setUUID(visitorUUID);
            }
            sl.addFreshEntity(vEntity);
            Questown.LOGGER.debug("Spawned visitor {} at {}", vEntity.getUUID(), surfacePos);

            entity.generateRandomQuest(sl);
            return true;
        }
        return false;
    }

    @Override
    protected Tag serializeNbt() {
        // No data. This reward is completely random when claimed.
        return new CompoundTag();
    }

    @Override
    protected void deserializeNbt(
            TownInterface entity,
            CompoundTag tag
    ) {
        // No data. This reward is completely random when claimed.
    }
}