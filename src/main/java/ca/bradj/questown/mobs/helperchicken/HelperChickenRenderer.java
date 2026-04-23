package ca.bradj.questown.mobs.helperchicken;

import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side renderer for the helper chicken.
 *
 * <p>Reuses the vanilla {@link ChickenRenderer} for the base model and adds
 * {@link HelperChickenBubbleLayer} for the world-space speech bubble above
 * the chicken's head.
 */
@OnlyIn(Dist.CLIENT)
public class HelperChickenRenderer extends ChickenRenderer {

    public HelperChickenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new HelperChickenBubbleLayer(this, ctx));
    }
}
