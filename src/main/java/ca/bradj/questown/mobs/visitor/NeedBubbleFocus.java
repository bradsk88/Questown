package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.rooms.DeadDoorBubbles;
import ca.bradj.questown.town.rooms.DoorTrouble;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Decides which single candidate is currently showing a need bubble (ADR-0011).
 *
 * <p>Exactly one bubble is shown at a time, so a twenty-townie town does not become a wall of
 * icons and the one thing that needs help is the thing the eye lands on. Townies (keyed by
 * entity UUID) and dead doors (keyed by {@link BlockPos}) compete in the SAME contest: the
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

    /** Townie holders are UUIDs; dead-door holders are BlockPos. No other types. */
    private static @Nullable Object current;
    private static double currentScore;
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
        for (VisitorMobEntity townie : mc.level.getEntitiesOfClass(
                VisitorMobEntity.class, mc.player.getBoundingBox().inflate(16.0D)
        )) {
            if (!townie.getNeed().isNeeded()) {
                continue;
            }
            double score = crosshairAlignment(mc.player, townie.getEyePosition());
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
            if (score < CONE_DOT || score <= bestScore) {
                continue;
            }
            best = door.getKey();
            bestScore = score;
            bestTownie = null;
            bestHintKey = door.getValue().hintKey();
            bestPos = Vec3.atCenterOf(door.getKey());
        }
        adopt(best, bestScore);
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
        ClientAccess.showHint(Compat.translatable(bestHintKey));
        hintCooldown = HINT_REPEAT_TICKS;
    }

    private static void adopt(@Nullable Object best, double bestScore) {
        if (keepsBubble(current, currentScore, best, bestScore)) {
            currentScore = best != null && best.equals(current) ? bestScore : currentScore;
            return;
        }
        current = best;
        currentScore = bestScore;
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

    /** The holder keeps the bubble only while it is still needy and still in front of the player. */
    public static void forgetIfResolved(VisitorMobEntity townie) {
        if (townie.getUUID().equals(current) && !townie.getNeed().isNeeded()) {
            forget();
        }
    }

    private static void forget() {
        current = null;
        currentScore = 0;
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
