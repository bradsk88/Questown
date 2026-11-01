package ca.bradj.questown.integration.minecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * A villager taking from or putting into a chest opens its lid with the chest sound and closes it
 * shortly after, as a player's use does. A chest a player has open is
 * left alone, so the villager does not close it under them.
 */
public final class ChestTouches {

    /** Vanilla's chest block event: type 1 carries the opener count, which drives the lid. */
    private static final int LID_EVENT = 1;

    private static final Map<ResourceKey<Level>, ChestLidTimers<BlockPos>> TIMERS = new HashMap<>();

    private ChestTouches() {
    }

    public static void touch(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof ChestBlock)) {
            return;
        }
        if (ChestBlockEntity.getOpenCount(level, pos) > 0) {
            return;
        }
        ChestLidTimers<BlockPos> timers = TIMERS.computeIfAbsent(level.dimension(), k -> new ChestLidTimers<>());
        if (timers.touch(pos, level.getGameTime())) {
            setLid(level, pos, 1, SoundEvents.CHEST_OPEN);
        }
    }

    /** Closes the lids that are due. Call once per server tick per level. */
    public static void tick(ServerLevel level) {
        ChestLidTimers<BlockPos> timers = TIMERS.get(level.dimension());
        if (timers == null) {
            return;
        }
        for (BlockPos pos : timers.due(level.getGameTime())) {
            if (!(level.getBlockState(pos).getBlock() instanceof ChestBlock)) {
                continue;
            }
            if (ChestBlockEntity.getOpenCount(level, pos) > 0) {
                continue;
            }
            setLid(level, pos, 0, SoundEvents.CHEST_CLOSE);
        }
    }

    private static void setLid(ServerLevel level, BlockPos pos, int openers, SoundEvent sound) {
        BlockState state = level.getBlockState(pos);
        level.blockEvent(pos, state.getBlock(), LID_EVENT, openers);
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            level.blockEvent(other, level.getBlockState(other).getBlock(), LID_EVENT, openers);
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }
}
