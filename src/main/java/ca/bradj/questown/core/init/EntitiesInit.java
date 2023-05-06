package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class EntitiesInit {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            ForgeRegistries.ENTITIES,
            Questown.MODID
    );

    public static final RegistryObject<EntityType<VisitorMobEntity>> VISITOR = ENTITY_TYPES.register(
            "visitor",
            () -> EntityType.Builder.of(
                            (EntityType<VisitorMobEntity> a, Level b) -> new VisitorMobEntity(a, b, null),
                            MobCategory.CREATURE
                    )
                    .sized(2f, 1f)
                    .build(new ResourceLocation(Questown.MODID, "visitor").toString())
    );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
