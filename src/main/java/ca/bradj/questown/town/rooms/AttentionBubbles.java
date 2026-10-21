package ca.bradj.questown.town.rooms;

import ca.bradj.questown.Questown;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side access to the "attention" state each loaded flag block entity syncs via its
 * update tag: whether the flag still has incomplete quests, and where its job boards are
 * plus whether the town has any outstanding work requests. Mirrors
 * {@link DeadDoorBubbles} — the server decides *what* needs attention; this class only
 * collects the copies that happen to be chunk-loaded on this client so
 * {@code NeedBubbleFocus} can treat flags and boards as candidates in the one shared
 * need-bubble (ADR-0011), drawn as the "!" bubble.
 *
 * <p>Same accepted cost as the dead-door bubbles: a player standing near a board of a
 * town whose flag chunk is NOT loaded sees no bubble — the state rides the flag's
 * block-entity update tag instead of a dedicated packet.
 */
@OnlyIn(Dist.CLIENT)
public final class AttentionBubbles {

    /** What the "!" bubble is pointing at. */
    public enum Kind {
        /** G1: the flag's town still has at least one incomplete quest. */
        INCOMPLETE_QUESTS("message.questown.attention.incomplete_quests", "textures/bubble/attention.png"),
        /** G2: the town has zero outstanding work requests on its job board(s). */
        NO_WORK_REQUESTS("message.questown.attention.no_work_requests", "textures/bubble/attention.png"),
        /** Full storage has blocked the town's villagers long enough to matter (StorageBlockTally). */
        STORAGE_FULL("message.questown.attention.storage_full", "textures/bubble/chest.png");

        public final String hintKey;
        public final ResourceLocation texture;

        Kind(String hintKey, String texturePath) {
            this.hintKey = hintKey;
            this.texture = new ResourceLocation(Questown.MODID, texturePath);
        }
    }

    /** An attention candidate: a block position plus what it is asking for. */
    public record Target(BlockPos pos, Kind kind) {
    }

    /**
     * The "!" icon — an authored bubble texture in the same folder as the chicken's
     * (see {@code ChickenArcBubbles.SUNSET_TEXTURE}), rendered through
     * {@code BubbleRenderer.renderTextureBubbleAtBlock}.
     */
    public static final ResourceLocation ATTENTION_TEXTURE = new ResourceLocation(
            Questown.MODID, "textures/bubble/attention.png"
    );

    private static final Set<TownFlagBlockEntity> LOADED_FLAGS = ConcurrentHashMap.newKeySet();

    private AttentionBubbles() {
    }

    /** Called from the flag block entity when it loads on the client. */
    public static void register(TownFlagBlockEntity flag) {
        LOADED_FLAGS.add(flag);
    }

    /** Called from the flag block entity when it unloads on the client. */
    public static void unregister(TownFlagBlockEntity flag) {
        LOADED_FLAGS.remove(flag);
    }

    /**
     * Every attention target within bubble range of the player, from all client-loaded
     * flags: each flag with incomplete quests, and each job board of a flag whose town
     * has zero work requests.
     */
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
