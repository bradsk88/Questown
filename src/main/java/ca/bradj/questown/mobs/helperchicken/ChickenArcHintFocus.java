package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the helper chicken's monologue hint on the overlay message when the
 * player is looking squarely at it and close enough — the chicken's version
 * of the townie need-bubble "explain when examined" gesture (ADR-0011). The
 * hint key is synced by the server (see
 * {@link HelperChickenEntity#getHintKey()}); the client only decides <em>when</em>
 * to show it.
 *
 * <p>There is one helper chicken per town, so there is no crosshair contest
 * here (unlike {@link ca.bradj.questown.mobs.visitor.NeedBubbleFocus}); the
 * chicken simply shows its hint when the player is looking at it. The click
 * handler still owns the plain-text fourth-wall cycle; this only surfaces the
 * monologue.
 */
@OnlyIn(Dist.CLIENT)
public final class ChickenArcHintFocus {

    /**
     * Ticks between re-showing the hint. The overlay message fades on its own,
     * so a focused chicken needs periodic re-showing; doing it every tick
     * would restart the fade animation constantly.
     */
    private static final int REPEAT_TICKS = 40;

    /** How close the player must be before the text is shown (square metres). */
    private static final double HINT_RANGE_SQR = 8.0 * 8.0;

    /** How squarely the player must be looking before the text is shown (dot product). */
    private static final double HINT_CONE_DOT = 0.985;

    /** The box within which the helper chicken is considered. */
    private static final double SEARCH_DISTANCE = 16.0D;

    private static int cooldown;

    private ChickenArcHintFocus() {
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        HelperChickenEntity chicken = nearestChicken(mc);
        if (chicken == null) {
            return;
        }
        String hintKey = chicken.getHintKey();
        if (hintKey == null || hintKey.isEmpty()) {
            return;
        }
        if (mc.player.distanceToSqr(chicken.position()) > HINT_RANGE_SQR) {
            return;
        }
        if (crosshairAlignment(mc.player, chicken.getEyePosition()) < HINT_CONE_DOT) {
            return;
        }
        ClientAccess.showHint(Compat.translatable(hintKey));
        cooldown = REPEAT_TICKS;
    }

    private static @Nullable HelperChickenEntity nearestChicken(Minecraft mc) {
        HelperChickenEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (HelperChickenEntity c : mc.level.getEntitiesOfClass(
                HelperChickenEntity.class, mc.player.getBoundingBox().inflate(SEARCH_DISTANCE)
        )) {
            double d = mc.player.distanceToSqr(c.position());
            if (d < best) {
                best = d;
                nearest = c;
            }
        }
        return nearest;
    }

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
