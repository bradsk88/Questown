package ca.bradj.questown.loader.forge;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
public class VisitorMobEntityEvents {

    @SubscribeEvent
    public static void entityAttrEvent(EntityAttributeCreationEvent event) {
        event.put(
                EntitiesInit.VISITOR.get(),
                VisitorMobEntity.setAttributes()
        );
    }

}
