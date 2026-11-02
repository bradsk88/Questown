package ca.bradj.questown.town.rooms;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client side: each loaded flag's dead doors. A door whose flag's chunk isn't loaded shows no bubble. */
@OnlyIn(Dist.CLIENT)
public final class DeadDoorBubbles {

    private static final Set<TownFlagBlockEntity> LOADED_FLAGS = ConcurrentHashMap.newKeySet();

    private DeadDoorBubbles() {
    }

    public static void register(TownFlagBlockEntity flag) {
        LOADED_FLAGS.add(flag);
    }

    public static void unregister(TownFlagBlockEntity flag) {
        LOADED_FLAGS.remove(flag);
    }

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
