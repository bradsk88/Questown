package ca.bradj.questown.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;

public interface QTWorldAccess {

    // Generic block property access
    OptionalInt getBlockIntProperty(BlockPos pos, String propertyName);
    OptionalInt getMaxBlockIntProperty(BlockPos pos, String propertyName);
    void setBlockIntProperty(BlockPos pos, String propertyName, int value);

    // Block queries
    boolean isAir(BlockPos pos);

    // Block drops & removal
    List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool);
    void removeBlock(BlockPos pos);

    // Tree chopping — removes all connected blocks of the same type and returns drops
    List<ItemStack> chopTree(BlockPos trunkPos);

    /** Whether a tree from {@code sapling} would grow at {@code pos}. Writes nothing. */

    /** Whether a tree from {@code sapling} would grow at {@code pos}. Writes nothing. */
    boolean canTreeGrowAt(BlockPos pos, ItemStack sapling);

    /** Grows the tree from {@code sapling} at {@code pos}. Returns whether it grew. */
    boolean growTreeAt(BlockPos pos, ItemStack sapling);

    /** A sapling planted during a warp, waiting to grow. */
    record PlantedSapling(BlockPos pos, ItemStack sapling, long plantTick) {}

    /** Saplings waiting to grow during a warp. Empty in real time. */
    Collection<PlantedSapling> getPlantedSaplings();

    /** Forgets a planted sapling once it has grown or been skipped. */
    void clearPlantedSapling(BlockPos pos);

    // Item use on block (planting, bone meal)
    boolean useItemOnBlock(ItemStack item, BlockPos pos);

    // Tool transformation
    boolean canToolTransformBlock(BlockPos pos, QTToolAction toolAction);
    void applyToolTransformation(BlockPos pos, QTToolAction toolAction);

    // Randomness
    <T> List<T> getShuffledCopy(Collection<T> items);

    // Container operations
    boolean isContainer(BlockPos pos);
    int getContainerSlotCount(BlockPos pos);
    ItemStack getContainerSlot(BlockPos pos, int slot);
    boolean insertIntoSlot(BlockPos pos, int slot, ItemStack item);
    ItemStack extractFromSlot(BlockPos pos, int slot, int count);
    boolean insertIntoContainer(BlockPos pos, ItemStack item);
    /** The slots of the display at pos (armor stand: feet, legs, chest, head; item frame: one), or empty. */
    List<ItemStack> getDisplaySlots(BlockPos pos);
    /** Puts the item in that slot of the display at pos; false when there is none. */
    boolean setDisplaySlot(BlockPos pos, int slot, ItemStack item);

    /** Drops the item at pos, so an item with nowhere to go isn't lost. */
    void dropItem(BlockPos pos, ItemStack item);

    // Sound
    void playSound(BlockPos pos, SoundEvent sound);
    void playSound(BlockPos pos, SoundEvent sound, SoundSource source);

    // Processing block advancement (furnaces, etc.)
    void advanceProcessing(BlockPos pos, int ticks);

    /**
     * Escape hatch for rules that cannot yet be expressed through QTWorldAccess methods.
     * Returns null during time warp and in tests — callers must handle that gracefully.
     * Prefer adding new methods to this interface instead of using this.
     */
    @Deprecated(forRemoval = true)
    @Nullable ServerLevel asServerLevel();
}