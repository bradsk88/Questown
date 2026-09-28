package ca.bradj.questown._vanilla.entities;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

public class EntitiesInit {

    public static final HolderRegistry<EntityType<?>> ENTITY_TYPES = Loader.entityTypes();

    public static final Holder<EntityType<FishingHook>> FISHIN_HOOK = ENTITY_TYPES.register(
            "fishing_hook",
            () -> EntityType.Builder.of(
                            (EntityType<FishingHook> a, Level b) -> new FishingHook(a, b),
                            MobCategory.MISC
                    )
                    .sized(0.6f, 1.6f)
                    .build(new ResourceLocation(Questown.MODID, "fishing_hook").toString())
    );

    public static void boot() {}
}
