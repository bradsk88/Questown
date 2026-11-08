package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
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

    // TODO: The helper chicken is not in this line yet. For its implementation see #409, #410, #411 and #412.

    public static void boot() {}
}
