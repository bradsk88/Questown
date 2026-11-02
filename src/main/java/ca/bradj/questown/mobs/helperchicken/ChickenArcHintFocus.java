package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/** Shows the helper chicken's hint on screen while the player looks straight at it from nearby. */
@OnlyIn(Dist.CLIENT)
public final class ChickenArcHintFocus {

    /** Ticks between re-showing the hint; it fades on its own. */
    private static final int REPEAT_TICKS = 40;

    /** How close the player must be (squared blocks). */
    private static final double HINT_RANGE_SQR = 8.0 * 8.0;

    /** How squarely the player must look (dot product). */
    private static final double HINT_CONE_DOT = 0.985;

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
