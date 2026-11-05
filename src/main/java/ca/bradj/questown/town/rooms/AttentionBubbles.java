package ca.bradj.questown.town.rooms;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Client side: the "!" bubble state of each loaded flag. A board whose flag's chunk isn't loaded shows no bubble. */
@OnlyIn(Dist.CLIENT)
public final class AttentionBubbles {

    public enum Kind {
        INCOMPLETE_QUESTS("message.questown.attention.incomplete_quests", "textures/bubble/attention.png"),
        NO_WORK_REQUESTS("message.questown.attention.no_work_requests", "textures/bubble/attention.png"),
        STORAGE_FULL("message.questown.attention.storage_full", "textures/menu/gatherer/no_space.png");

        public final String hintKey;
        public final ResourceLocation texture;

        Kind(String hintKey, String texturePath) {
            this.hintKey = hintKey;
            this.texture = new ResourceLocation(Questown.MODID, texturePath);
        }
    }

    public record Target(BlockPos pos, Kind kind) {
    }

    public static final ResourceLocation ATTENTION_TEXTURE = new ResourceLocation(
            Questown.MODID, "textures/bubble/attention.png"
    );

    private static final Set<TownFlagBlockEntity> LOADED_FLAGS = ConcurrentHashMap.newKeySet();

    private AttentionBubbles() {
    }

    public static void register(TownFlagBlockEntity flag) {
        LOADED_FLAGS.add(flag);
    }

    public static void unregister(TownFlagBlockEntity flag) {
        LOADED_FLAGS.remove(flag);
    }

    /** Every "!" target within bubble range of the player. */
    public static List<Target> near(Player player) {
        ImmutableList.Builder<Target> b = ImmutableList.builder();
        for (TownFlagBlockEntity flag : LOADED_FLAGS) {
            if (flag.isAttentionStorageFull()
                    && flag.getBlockPos().distSqr(player.blockPosition()) <= 16.0 * 16.0) {
                b.add(new Target(flag.getBlockPos(), Kind.STORAGE_FULL));
            }
            if (flag.isAttentionQuestsIncomplete()
                    && flag.getBlockPos().distSqr(player.blockPosition()) <= 16.0 * 16.0) {
                b.add(new Target(flag.getBlockPos(), Kind.INCOMPLETE_QUESTS));
            }
            if (flag.isAttentionNoWorkRequests()) {
                for (BlockPos board : flag.getAttentionBoardPositions()) {
                    if (board.distSqr(player.blockPosition()) <= 16.0 * 16.0) {
                        b.add(new Target(board, Kind.NO_WORK_REQUESTS));
                    }
                }
            }
        }
        return b.build();
    }
}
