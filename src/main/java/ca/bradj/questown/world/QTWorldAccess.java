package ca.bradj.questown.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.OptionalInt;

public interface QTWorldAccess {

    // Generic block property access
    OptionalInt getBlockIntProperty(BlockPos pos, String propertyName);
    OptionalInt getMaxBlockIntProperty(BlockPos pos, String propertyName);
    void setBlockIntProperty(BlockPos pos, String propertyName, int value);

    // Block drops & removal
    List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool);
    void removeBlock(BlockPos pos);

    // Item use on block (planting, bone meal)
    boolean useItemOnBlock(ItemStack item, BlockPos pos);

    // Tool transformation
    boolean canToolTransformBlock(BlockPos pos, QTToolAction toolAction);
    void applyToolTransformation(BlockPos pos, QTToolAction toolAction);

    // Sound
    void playSound(BlockPos pos, SoundEvent sound);
    void playSound(BlockPos pos, SoundEvent sound, SoundSource source);

    /**
     * Escape hatch for rules that cannot yet be expressed through QTWorldAccess methods.
     * Returns null during time warp and in tests — callers must handle that gracefully.
     * Prefer adding new methods to this interface instead of using this.
     */
    @Deprecated(forRemoval = true)
    @Nullable ServerLevel asServerLevel();
}