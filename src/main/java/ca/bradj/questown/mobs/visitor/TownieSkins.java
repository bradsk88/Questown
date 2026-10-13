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
 * <p>The vanilla 1.20.6 character skins (added alongside Steve/Alex in
 * 1.20.5) live under {@code minecraft:textures/entity/player/slim/}; the
 * townie model is baked from {@code PLAYER_SLIM}, so the slim variants are
 * the matching textures. The mod's own {@code textures/entity/1-6.png} skins
 * stay in the pool alongside them.
 */
final class TownieSkins {

    /** The vanilla 1.20.6 character skins (slim variants). */
    static final List<ResourceLocation> VANILLA_SKINS = ImmutableList.of(
            new ResourceLocation("textures/entity/player/slim/alex.png"),
            new ResourceLocation("textures/entity/player/slim/ari.png"),
            new ResourceLocation("textures/entity/player/slim/efe.png"),
            new ResourceLocation("textures/entity/player/slim/kai.png"),
            new ResourceLocation("textures/entity/player/slim/makena.png"),
            new ResourceLocation("textures/entity/player/slim/noor.png"),
            new ResourceLocation("textures/entity/player/slim/steve.png"),
            new ResourceLocation("textures/entity/player/slim/sunny.png"),
            new ResourceLocation("textures/entity/player/slim/zuri.png")
    );

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
