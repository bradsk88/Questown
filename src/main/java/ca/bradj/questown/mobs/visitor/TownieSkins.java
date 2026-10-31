package ca.bradj.questown.mobs.visitor;

import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * The texture pool a townie skin is picked from, and the deterministic
 * per-UUID pick. Pure (no client/Forge state) so the pool and the pick are
 * unit-testable without instantiating the renderer.
 *
 * <p>The vanilla character skins come from {@code Compat.vanillaCharacterSkins()} (nine slim
 * skins on 1.20.6, Steve and Alex on 1.19.2). The mod's own {@code textures/entity/1-6.png}
 * skins stay in the pool alongside them.
 */
final class TownieSkins {

    /** The vanilla character skins this Minecraft version ships ({@link ca.bradj.questown.mc.Compat}). */
    static final List<ResourceLocation> VANILLA_SKINS = ca.bradj.questown.mc.Compat.vanillaCharacterSkins();

    /** The mod's custom townie skins (kept on top of the vanilla pool). */
    static final List<ResourceLocation> CUSTOM_SKINS = ImmutableList.of(
            new ResourceLocation("questown", "textures/entity/1.png"),
            new ResourceLocation("questown", "textures/entity/2.png"),
            new ResourceLocation("questown", "textures/entity/3.png"),
            new ResourceLocation("questown", "textures/entity/4.png"),
            new ResourceLocation("questown", "textures/entity/5.png"),
            new ResourceLocation("questown", "textures/entity/6.png")
    );

    private TownieSkins() {
    }

    /** The full pool: vanilla skins first, then the mod's custom skins. */
    static List<ResourceLocation> pool() {
        return ImmutableList.<ResourceLocation>builder()
                .addAll(VANILLA_SKINS)
                .addAll(CUSTOM_SKINS)
                .build();
    }

    /**
     * The deterministic skin for an entity UUID - the same townie always gets
     * the same skin. Floor-mod like vanilla's character-skin picker (a plain
     * {@code Math.abs} mis-picks on a {@code Integer.MIN_VALUE} hash).
     */
    static ResourceLocation pick(UUID uuid) {
        List<ResourceLocation> skins = pool();
        int index = Math.floorMod(uuid.hashCode(), skins.size());
        return skins.get(index);
    }
}
