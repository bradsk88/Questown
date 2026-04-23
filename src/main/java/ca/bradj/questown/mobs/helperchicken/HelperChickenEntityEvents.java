package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class HelperChickenEntityEvents {

    @SubscribeEvent
    public static void entityAttrEvent(EntityAttributeCreationEvent event) {
        event.put(
                EntitiesInit.HELPER_CHICKEN.get(),
                Chicken.createAttributes().build()
        );
    }
}
