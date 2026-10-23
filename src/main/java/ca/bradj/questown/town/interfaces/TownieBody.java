package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.mobs.visitor.TownieNeed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The townie's body: the entity that stands in the world for a townie whose state and decisions
 * live on the town flag ({@link ca.bradj.questown.town.entity.TownieRuntime}). The flag reads where
 * the body is and tells it where to walk and what to show; the body moves, renders and reports, and
 * decides nothing. See {@code town/entity/COMPLEX.md} "Townie runtime and body".
 */
public interface TownieBody {
    BlockPos blockPosition();

    Vec3 position();

    /** The body has left the world (unloaded, killed or discarded). */
    boolean isRemoved();

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

    TownieNeed getNeed();

    void setNeed(TownieNeed need);
}
