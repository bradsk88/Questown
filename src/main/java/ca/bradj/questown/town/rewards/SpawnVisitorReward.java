package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Reward;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Random;

public class SpawnVisitorReward extends Reward {

    public static final String ID = "spawn_visitor_reward";

    public SpawnVisitorReward(RewardType<? extends Reward> rType, @NotNull TownFlagBlockEntity entity) {
        super(rType, () -> {
            while(!spawnVisitorNearby(entity)) {} // FIXME: Instead of "while" loop, set state on entity and try adding during ticks
        });
    }
    public SpawnVisitorReward(TownFlagBlockEntity entity) {
        this(RewardsInit.VISITOR.get(), entity);
    }

    private static boolean spawnVisitorNearby(TownFlagBlockEntity entity) {
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
            vEntity.moveTo(surfacePos.getX() + 0.5, surfacePos.getY() + 1, surfacePos.getZ() + 0.5, random.nextFloat() * 360, 0);
            sl.addFreshEntity(vEntity);
            Questown.LOGGER.debug("Spawned visitor at " + surfacePos);
            return true;
        }
        return false;
    }
}
