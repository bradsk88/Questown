package ca.bradj.questown.mobs.visitor;

import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** The skins a villager can have: the vanilla character skins and the mod's own, picked by UUID. */
final class TownieSkins {

    static final List<ResourceLocation> VANILLA_SKINS = ca.bradj.questown.mc.Compat.vanillaCharacterSkins();

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

    static List<ResourceLocation> pool() {
        return ImmutableList.<ResourceLocation>builder()
                .addAll(VANILLA_SKINS)
                .addAll(CUSTOM_SKINS)
                .build();
    }

    /** The skin for a UUID; the same villager always gets the same skin. */
    static ResourceLocation pick(UUID uuid) {
        List<ResourceLocation> skins = pool();
        int index = Math.floorMod(uuid.hashCode(), skins.size());
        return skins.get(index);
    }
}
