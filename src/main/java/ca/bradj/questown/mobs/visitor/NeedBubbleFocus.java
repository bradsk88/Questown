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
 * Decides which single candidate is currently showing a need bubble (ADR-0011).
 *
 * <p>Exactly one bubble is shown at a time, so a twenty-townie town does not become a wall of
 * icons and the one thing that needs help is the thing the eye lands on. Townies (keyed by
 * entity UUID), dead doors (keyed by {@link BlockPos}) and attention targets (a flag with
 * incomplete quests, or a job board with no work requests, keyed by an
 * {@link AttentionBubbles.Target}) compete in the SAME contest: the
 * winner is whichever candidate is closest to the crosshair — proximity, not hover, so the
 * player can sweep the crosshair across the square rather than walking up to each problem.
 *
 * <p>Client-side only: which candidate the player is looking at is not the server's business,
 * and the server already publishes everything needed (the synched townie need, the flag's
 * dead-door set) to decide it here.
 */
@OnlyIn(Dist.CLIENT)
public final class NeedBubbleFocus {

    /**
     * How far off the crosshair a candidate may be and still be eligible, as a dot product
     * against the view vector. ~0.9 is a cone of roughly 25 degrees: wide enough to catch a
     * townie the player is looking near, narrow enough that "which one did I mean" is
     * unambiguous.
     */
    private static final double CONE_DOT = 0.9;

    /**
     * How much better a challenger must be before it takes the bubble from the current holder.
     * Without this, two candidates standing together swap the bubble every frame as the
     * crosshair drifts, which reads as flickering rather than as information.
     */
    private static final double TAKEOVER_MARGIN = 0.02;

    /**
     * How close the player must be, and how squarely they must be looking, before the bubble is
     * spelled out in words. The bubble is for sweeping the square; the text is for "I have walked
     * over and I am looking right at it", which is a deliberate second gesture.
     */
    private static final double HINT_RANGE_SQR = 8.0 * 8.0;
    private static final double HINT_CONE_DOT = 0.985;

    /**
     * Ticks between re-showing the text. The action bar fades on its own, so a focused candidate
     * needs periodic re-showing; doing it every tick would restart the fade animation constantly.
     */
    private static final int HINT_REPEAT_TICKS = 40;

    /** Townie holders are UUIDs; dead-door holders are BlockPos; attention holders are Targets. */
    private static @Nullable Object current;
    private static int hintCooldown;

    private NeedBubbleFocus() {
    }

    public static boolean isShowingBubble(VisitorMobEntity townie) {
        return townie.getUUID().equals(current);
    }

    /** The dead door currently holding the bubble, if the holder is a door. */
    public static @Nullable BlockPos currentDoor() {
        return current instanceof BlockPos pos ? pos : null;
    }

    /** The attention target (flag / job board) currently holding the bubble, if any. */
    public static @Nullable AttentionBubbles.Target currentAttention() {
        return current instanceof AttentionBubbles.Target t ? t : null;
    }

    /**
     * Re-picks the winner. Call once per client tick, not per frame — per frame would re-evaluate
     * mid-interpolation and make the hysteresis meaningless.
     */
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
        // The holder's score this tick (0 when it no longer qualifies). A score saved when it took
        // the bubble went stale: one looked at dead-on kept the bubble against everything else
        // in the cone (2026-10-31: the flag's held it while the crosshair sat on a needy cook).
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

    /**
     * Says in words what the bubble means, once the player has closed in and looked straight at
     * the holder. Only the bubble holder is ever explained, so walking into a crowd cannot
     * produce a stream of competing messages.
     */
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

    /**
     * The missing item a {@link TownieNeed#UNMET_ITEM} bubble is about, or null for any other need
     * or when the item cannot be resolved on the client.
     */
    static @Nullable Ingredient unmetNeedIngredient(VisitorMobEntity townie) {
        if (townie == null) {
            return null;
        }
        return townie.getUnmetItemIngredient();
    }

    /**
     * The on-look hint for a bubble holder. A need with a key names it ({@link TownieNeedDetail}:
     * the missing room or item, or the room it cannot get into); anything else keeps the generic
     * per-need key.
     */
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

    /** {@code score} when {@code candidate} holds the bubble and still qualifies, else {@code soFar}. */
    private static double holderScoreIf(Object candidate, double score, double soFar) {
        if (!candidate.equals(current) || score < CONE_DOT) {
            return soFar;
        }
        return score;
    }

    /**
     * Whether the current holder keeps the bubble. Pure so the flicker rule can be tested without
     * a level: the holder keeps it unless a challenger beats it by {@link #TAKEOVER_MARGIN}, and
     * loses it immediately once nothing qualifies.
     */
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

    /**
     * Villager bubbles show only in the working day (morning and noon): from the evening on, the
     * town is eating and sleeping, so a work need is not stopping anything (dev playtest
     * 2026-10-29). Door and attention bubbles are not affected.
     */
    static boolean villagerNeedsShowAt(long dayTime) {
        Signals s = Signals.fromDayTime(new Signals.DayTime(dayTime));
        return s == Signals.MORNING || s == Signals.NOON;
    }

    /**
     * The bubble icon for a MISSING_ROOM need: the room's blocks in turn, one a second, so the
     * bubble shows what to build (dev playtest 2026-10-29: "Their bubble can show the room blocks
     * too"). Null when the room or its recipe is unknown here.
     */
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

    /** The item shown in second {@code seconds} when cycling through {@code items}; null when empty. */
    static <T> @Nullable T cycle(java.util.List<T> items, long seconds) {
        if (items.isEmpty()) {
            return null;
        }
        return items.get((int) (seconds % items.size()));
    }

    /** The holder keeps the bubble only while it is still needy and still in front of the player. */
    public static void forgetIfResolved(VisitorMobEntity townie) {
        if (townie.getUUID().equals(current) && !townie.getNeed().isNeeded()) {
            forget();
        }
    }

    private static void forget() {
        current = null;
    }

    /**
     * 1.0 when the target is dead ahead, falling off as it moves away from the crosshair. Same
     * shape as {@code VisitorMobEntity.isLookingAtMe}, minus its distance gate — the caller has
     * already bounded the search.
     */
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
