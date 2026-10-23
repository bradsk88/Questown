package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.mobs.visitor.TownieNeed;
import ca.bradj.questown.town.PoseInPlace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The townie's body: the entity that stands in the world for a townie whose state and decisions
 * live on the town flag ({@link ca.bradj.questown.town.entity.TownieRuntime}). The flag reads where
 * the body is and tells it where to walk, how to stand and what to show; the body moves, renders
 * and reports, and decides nothing. See {@code town/entity/COMPLEX.md} "Townie runtime and body".
 */
public interface TownieBody {
    BlockPos blockPosition();

    Vec3 position();

    /** The body has left the world (unloaded, killed or discarded). */
    boolean isRemoved();

    /**
     * The game tick in which the body last ticked as an awake townie of an initialized town
     * (loaded in an entity-ticking chunk, not asleep in a bed); -1 before its first such tick. The
     * flag makes the townie's decisions only in a game tick its body ticked in.
     */
    long lastTickedAt();

    /** The status the body is showing, or null when it shows none. */
    @Nullable String getShownStatus();

    /** Take the pose the townie's job asked for: into a bed, or out of it. */
    void takePose(PoseInPlace pose);

    /** A work stroke: the arm swing a player sees when the townie works a block. */
    void swingMainHand();

    /** Wear one point of durability off a tool the townie is working with. */
    void wearTool(ItemStack tool);

    /** Drop an item at the body's feet (what a full inventory could not take). */
    void dropItem(ItemStack stack);

    /** Where the body is currently walking, if anywhere. */
    Optional<BlockPos> getWalkTarget();

    void setWalkTarget(BlockPos target);

    /** Stop walking (the target is forgotten; the wander target is kept). */
    void clearWalkTarget();

    void setVisible(boolean visible);

    void showJobName(String translationKey);

    void showStatus(String status);

    void showMainHandItem(ItemStack item);

    void showOffhandItem(ItemStack item);

    /** Show the townie's need bubble (and, for UNMET_ITEM, which item). */
    void showNeed(
            TownieNeed need,
            String unmetItem
    );
}
