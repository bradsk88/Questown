package ca.bradj.questown.mobs.helperchicken;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Custom {@link RenderType} used by the helper-chicken speech-bubble layer
 * to render icons through walls when the entity's through-walls flag is set.
 *
 * <p>Key composite-state choices:
 * <ul>
 *   <li>{@code NO_DEPTH_TEST} — geometry draws regardless of occluding blocks.</li>
 *   <li>{@code COLOR_WRITE} — depth writes are OFF. Writing depth from an
 *       always-on-top pass breaks depth ordering for subsequent translucent
 *       draws in the frame.</li>
 *   <li>{@code RENDERTYPE_ENTITY_TRANSLUCENT_SHADER} — reuses the shader path
 *       that item icons normally use, so item UVs line up identically.</li>
 *   <li>{@code TRANSLUCENT_TRANSPARENCY} — respects the icon's alpha channel.</li>
 *   <li>{@code NO_CULL} — the bubble billboards toward the camera; back-face
 *       culling would hide it at certain angles.</li>
 * </ul>
 *
 * <p>Extends {@link RenderStateShard} solely so the inherited protected state
 * constants (e.g. {@code NO_DEPTH_TEST}, {@code COLOR_WRITE}) are visible here —
 * the same trick vanilla {@code RenderType} uses. We never instantiate the
 * super class; {@link #throughWalls(ResourceLocation)} is a static factory.
 *
 * <p>Exposed as a factory that binds to an explicit texture so a single
 * {@code RenderType} instance is shared per texture rather than per call.
 */
@OnlyIn(Dist.CLIENT)
public final class BubbleRenderType extends RenderStateShard {

    private BubbleRenderType() {
        // Never instantiated — subclassing is only for protected-field access.
        super("qt_bubble", () -> {
        }, () -> {
        });
    }

    /**
     * Creates a through-walls render type bound to the given texture.
     *
     * <p>Callers should cache the returned instance — vanilla
     * {@link RenderType#create(String, VertexFormat, VertexFormat.Mode, int, boolean, boolean, RenderType.CompositeState)}
     * does its own memoization internally, so repeated calls with the same
     * arguments are cheap but not free.
     */
    public static RenderType throughWalls(ResourceLocation texture) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false);
        return RenderType.create(
                "qt_bubble_xray",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                state
        );
    }

    /**
     * Solid-white quads in entity space, used for the chicken bubble's
     * comic-style background shape. POSITION_COLOR vertex format — no
     * texture — so vertices carry their own white color.
     *
     * <p>Depth-write is OFF so the bubble background never occludes the
     * item icon drawn on top of it (icon may share or precede the bubble's
     * depth value depending on the chosen item TransformType).
     *
     * @param throughWalls when {@code true}, depth test is disabled so the
     *     bubble shape draws over occluding geometry — matches the
     *     through-walls behaviour of {@link #throughWalls(ResourceLocation)}.
     */
    public static RenderType solidWhite(boolean throughWalls) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(throughWalls
                        ? RenderStateShard.NO_DEPTH_TEST
                        : RenderStateShard.LEQUAL_DEPTH_TEST)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setOverlayState(RenderStateShard.NO_OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false);
        return RenderType.create(
                throughWalls ? "qt_bubble_bg_xray" : "qt_bubble_bg",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                state
        );
    }
}
