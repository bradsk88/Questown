package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.jobs.Signals;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.rooms.AttentionBubbles;
import ca.bradj.questown.town.rooms.DeadDoorBubbles;
import ca.bradj.questown.town.rooms.DoorTrouble;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Picks the one need bubble shown at a time: the villager, dead door or "!" target nearest the
 * crosshair. Client side only.
 */
@OnlyIn(Dist.CLIENT)
public final class NeedBubbleFocus {

    /** How far off the crosshair a candidate may be, as a dot product: about 25 degrees. */
    private static final double CONE_DOT = 0.9;

    /** How much better a challenger must be to take the bubble, so two neighbours don't flicker. */
    private static final double TAKEOVER_MARGIN = 0.02;

    /** How close the player must be, looking straight at it, before the bubble is put into words. */
    private static final double HINT_RANGE_SQR = 8.0 * 8.0;
    private static final double HINT_CONE_DOT = 0.985;

    /** Ticks between re-showing the words; the action bar fades on its own. */
    private static final int HINT_REPEAT_TICKS = 40;

    /** A villager's UUID, a dead door's BlockPos, or an attention Target. */
    private static @Nullable Object current;
    private static int hintCooldown;

    private NeedBubbleFocus() {
    }

    public static boolean isShowingBubble(VisitorMobEntity townie) {
        return townie.getUUID().equals(current);
    }

    public static @Nullable BlockPos currentDoor() {
        return current instanceof BlockPos pos ? pos : null;
    }

    public static @Nullable AttentionBubbles.Target currentAttention() {
        return current instanceof AttentionBubbles.Target t ? t : null;
    }

    /** Picks the holder again. Call once per client tick. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            forget();
            return;
        }
        @Nullable Object best = null;
        double bestScore = 0;
        @Nullable VisitorMobEntity bestTownie = null;
        @Nullable String bestHintKey = null;
        @Nullable Vec3 bestPos = null;
        // The holder's score this tick; 0 once it no longer qualifies.
        double holderScore = 0;
        boolean townieNeedsShow = villagerNeedsShowAt(mc.level.getDayTime());
        for (VisitorMobEntity townie : mc.level.getEntitiesOfClass(
                VisitorMobEntity.class, mc.player.getBoundingBox().inflate(16.0D)
        )) {
            if (!townieNeedsShow || !townie.getNeed().isNeeded()) {
                continue;
            }
            double score = crosshairAlignment(mc.player, townie.getEyePosition());
            holderScore = holderScoreIf(townie.getUUID(), score, holderScore);
            if (score < CONE_DOT || score <= bestScore) {
                continue;
            }
            best = townie.getUUID();
            bestScore = score;
            bestTownie = townie;
            bestHintKey = townie.getNeed().hintKey();
            bestPos = townie.position();
        }
        for (Map.Entry<BlockPos, DoorTrouble> door : DeadDoorBubbles.near(mc.player).entrySet()) {
            Vec3 bubbleAnchor = Vec3.atBottomCenterOf(door.getKey()).add(0, 2.0, 0);
            double score = crosshairAlignment(mc.player, bubbleAnchor);
            holderScore = holderScoreIf(door.getKey(), score, holderScore);
            if (score < CONE_DOT || score <= bestScore) {
                continue;
            }
            best = door.getKey();
            bestScore = score;
            bestTownie = null;
            bestHintKey = door.getValue().hintKey();
            bestPos = Vec3.atCenterOf(door.getKey());
        }
        for (AttentionBubbles.Target target : AttentionBubbles.near(mc.player)) {
            Vec3 bubbleAnchor = Vec3.atBottomCenterOf(target.pos()).add(0, 2.0, 0);
            double score = crosshairAlignment(mc.player, bubbleAnchor);
            holderScore = holderScoreIf(target, score, holderScore);
            if (score < CONE_DOT || score <= bestScore) {
                continue;
            }
            best = target;
            bestScore = score;
            bestTownie = null;
            bestHintKey = target.kind().hintKey;
            bestPos = Vec3.atCenterOf(target.pos());
        }
        adopt(best, bestScore, holderScore);
        explainIfExamined(mc, bestTownie, best, bestPos, bestHintKey, bestScore);
    }

    /** Says what the bubble means once the player is close and looking straight at the holder. */
    private static void explainIfExamined(
            Minecraft mc,
            @Nullable VisitorMobEntity townie,
            @Nullable Object best,
            @Nullable Vec3 bestPos,
            @Nullable String bestHintKey,
            double alignment
    ) {
        if (hintCooldown > 0) {
            hintCooldown--;
        }
        if (townie != null && !isShowingBubble(townie)) {
            return;
        }
        if (townie == null && (best == null || !best.equals(current))) {
            return;
        }
        if (bestPos == null || bestHintKey == null || mc.player == null) {
            return;
        }
        if (alignment < HINT_CONE_DOT || mc.player.distanceToSqr(bestPos) > HINT_RANGE_SQR) {
            return;
        }
        if (hintCooldown > 0) {
            return;
        }
        ClientAccess.showHint(hintComponent(townie, bestHintKey));
        hintCooldown = HINT_REPEAT_TICKS;
    }

    /** The missing item behind an {@link TownieNeed#UNMET_ITEM} bubble, or null. */
    static @Nullable Ingredient unmetNeedIngredient(VisitorMobEntity townie) {
        if (townie == null) {
            return null;
        }
        return townie.getUnmetItemIngredient();
    }

    /** The words for a holder's bubble: what it is missing or can't reach, else the need's own text. */
    private static Component hintComponent(
            @Nullable VisitorMobEntity townie,
            String hintKey
    ) {
        Component detail = townie == null ? null : TownieNeedDetail.of(townie);
        return detail != null ? detail : Compat.translatable(hintKey);
    }

    private static void adopt(@Nullable Object best, double bestScore, double holderScore) {
        if (keepsBubble(current, holderScore, best, bestScore)) {
            return;
        }
        current = best;
    }

    private static double holderScoreIf(Object candidate, double score, double soFar) {
        if (!candidate.equals(current) || score < CONE_DOT) {
            return soFar;
        }
        return score;
    }

    /** Whether the holder keeps the bubble: unless a challenger beats it by {@link #TAKEOVER_MARGIN}, or nothing qualifies any more. */
    static boolean keepsBubble(
            @Nullable Object holder,
            double holderScore,
            @Nullable Object challenger,
            double challengerScore
    ) {
        if (holder == null) {
            return challenger == null;
        }
        if (challenger == null) {
            return false;
        }
        if (challenger.equals(holder)) {
            return true;
        }
        return challengerScore < holderScore + TAKEOVER_MARGIN;
    }

    /** Villager bubbles show only in the working day; door and "!" bubbles always. */
    static boolean villagerNeedsShowAt(long dayTime) {
        Signals s = Signals.fromDayTime(new Signals.DayTime(dayTime));
        return s == Signals.MORNING || s == Signals.NOON;
    }

    /** A missing-room bubble cycles through the room's blocks, one a second. Null when the room is unknown. */
    static @Nullable net.minecraft.world.item.ItemStack missingRoomIcon(VisitorMobEntity townie) {
        net.minecraft.resources.ResourceLocation room = townie.getMissingRoom();
        if (room == null) {
            return null;
        }
        java.util.List<net.minecraft.world.item.ItemStack> blocks = new java.util.ArrayList<>();
        ca.bradj.questown.logic.RoomRecipes.getByIdOrSpecial(townie.level(), room).ifPresent(r -> {
            for (Ingredient i : ca.bradj.questown.logic.RoomRecipes.filterSpecialBlocks(r.getIngredients())) {
                net.minecraft.world.item.ItemStack[] stacks = i.getItems();
                if (stacks.length > 0) {
                    blocks.add(stacks[0]);
                }
            }
        });
        return cycle(blocks, System.currentTimeMillis() / 1000);
    }

    static <T> @Nullable T cycle(java.util.List<T> items, long seconds) {
        if (items.isEmpty()) {
            return null;
        }
        return items.get((int) (seconds % items.size()));
    }

    public static void forgetIfResolved(VisitorMobEntity townie) {
        if (townie.getUUID().equals(current) && !townie.getNeed().isNeeded()) {
            forget();
        }
    }

    private static void forget() {
        current = null;
    }

    /** 1 when the target is dead ahead, falling off away from the crosshair. */
    private static double crosshairAlignment(Player player, Vec3 target) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 toTarget = new Vec3(
                target.x - player.getX(),
                target.y - player.getEyeY(),
                target.z - player.getZ()
        ).normalize();
        return look.dot(toTarget);
    }
}
