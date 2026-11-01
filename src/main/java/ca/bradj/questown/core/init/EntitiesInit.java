package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mobs.helperchicken.HelperChickenEntity;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

public class EntitiesInit {

    public static final HolderRegistry<EntityType<?>> ENTITY_TYPES = Loader.entityTypes();

    public static final Holder<EntityType<VisitorMobEntity>> VISITOR = ENTITY_TYPES.register(
            "visitor",
            () -> EntityType.Builder.of(
                            (EntityType<VisitorMobEntity> a, Level b) -> new VisitorMobEntity(a, b, null),
                            MobCategory.CREATURE
                    )
                    .sized(0.6f, 1.6f)
                    .build(new ResourceLocation(Questown.MODID, "visitor").toString())
    );

    public static final Holder<EntityType<HelperChickenEntity>> HELPER_CHICKEN = ENTITY_TYPES.register(
            "helper_chicken",
            () -> EntityType.Builder.of(HelperChickenEntity::new, MobCategory.CREATURE)
                    .sized(0.4f, 0.7f)
                    .build(new ResourceLocation(Questown.MODID, "helper_chicken").toString())
    );

    public static void boot() {}
}
