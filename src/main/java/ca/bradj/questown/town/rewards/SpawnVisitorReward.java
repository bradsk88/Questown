package ca.bradj.questown.town.rewards;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.core.init.RewardsInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Reward;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.Random;

public class SpawnVisitorReward extends Reward {

    public static final String ID = "spawn_visitor_reward";

    public SpawnVisitorReward(RewardType<? extends Reward> rType, Level level, BlockPos flagPos) {
        super(rType, () -> {
            while(!spawnVisitorNearby(level, flagPos)) {}
        });
    }

    public SpawnVisitorReward(RewardType<? extends Reward> rType, TownFlagBlockEntity entity) {
        this(rType, entity.getLevel(), entity.getBlockPos());
    }
    public SpawnVisitorReward(TownFlagBlockEntity entity) {
        this(RewardsInit.VISITOR.get(), entity.getLevel(), entity.getBlockPos());
    }

    private static boolean spawnVisitorNearby(Level level, BlockPos center) {
        int radius = 30; // TODO: Add to config?
        Random random = level.getRandom();

        Optional<TownFlagBlockEntity> oEntity = level.getBlockEntity(center, TilesInit.TOWN_FLAG.get());
        if (oEntity.isEmpty()) {
            return false;
        }
        TownFlagBlockEntity flagEnt = oEntity.get();

        // Get a random position within the specified radius around the center
        BlockPos pos = center.offset(random.nextInt(radius * 2) - radius, 0, random.nextInt(radius * 2) - radius);

        // Find the top block at the random position
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        BlockPos surfacePos = pos.atY(surfaceY);
        BlockState surfaceBlockState = level.getBlockState(surfacePos);

        // Check if the top block is a solid block, such as grass or dirt
        if (surfaceBlockState.getMaterial().isSolid()) {
            // Spawn the entity on top of the solid block
            VisitorMobEntity entity = new VisitorMobEntity(EntitiesInit.VISITOR.get(), level, flagEnt);
            entity.moveTo(surfacePos.getX() + 0.5, surfacePos.getY() + 1, surfacePos.getZ() + 0.5, random.nextFloat() * 360, 0);
            level.addFreshEntity(entity);
            Questown.LOGGER.debug("Spawned visitor at " + surfacePos);
            return true;
        }
        return false;
    }
}
