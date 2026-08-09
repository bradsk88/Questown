package ca.bradj.questown.town.rooms;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side access to the dead-door sets that each loaded flag block entity syncs via its
 * update tag (ADR-0011). The server decides *which* doors are dead and why; this class only
 * collects the copies that happen to be chunk-loaded on this client so {@code NeedBubbleFocus}
 * can treat doors and townies as candidates in the one shared bubble.
 *
 * <p>A player standing near a door of a town whose flag chunk is NOT loaded sees no bubble —
 * that is the accepted cost of riding the block-entity update tag instead of a dedicated
 * packet (2026-08-09 grilling).
 */
@OnlyIn(Dist.CLIENT)
public final class DeadDoorBubbles {

    private static final Set<TownFlagBlockEntity> LOADED_FLAGS = ConcurrentHashMap.newKeySet();

    private DeadDoorBubbles() {
    }

    /** Called from the flag block entity when it loads on the client. */
    public static void register(TownFlagBlockEntity flag) {
        LOADED_FLAGS.add(flag);
    }

    /** Called from the flag block entity when it unloads on the client. */
    public static void unregister(TownFlagBlockEntity flag) {
        LOADED_FLAGS.remove(flag);
    }

    /** Every dead door within bubble range of the player, from all client-loaded flags. */
    public static Map<BlockPos, DoorTrouble> near(Player player) {
        ImmutableMap.Builder<BlockPos, DoorTrouble> b = ImmutableMap.builder();
        for (TownFlagBlockEntity flag : LOADED_FLAGS) {
            flag.getDeadDoors().forEach((pos, trouble) -> {
                if (pos.distSqr(player.blockPosition()) <= 16.0 * 16.0) {
                    b.put(pos, trouble);
                }
            });
        }
        return b.build();
    }
}
