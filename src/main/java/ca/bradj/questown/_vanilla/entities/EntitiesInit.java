package ca.bradj.questown._vanilla.entities;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mc.Compat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class EntitiesInit {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            Compat.entityTypes(),
            Questown.MODID
    );

    public static final RegistryObject<EntityType<FishingHook>> FISHIN_HOOK = ENTITY_TYPES.register(
            "fishing_hook",
            () -> EntityType.Builder.of(
                            (EntityType<FishingHook> a, Level b) -> new FishingHook(a, b),
                            MobCategory.MISC
                    )
                    .sized(0.6f, 1.6f)
                    .build(new ResourceLocation(Questown.MODID, "fishing_hook").toString())
    );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
