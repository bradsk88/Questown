package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.init.EntitiesInit;
import net.minecraft.world.entity.animal.Chicken;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

public class HelperChickenEntityEvents {

    @EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static class ModBus {
        @SubscribeEvent
        public static void entityAttrEvent(EntityAttributeCreationEvent event) {
            event.put(
                    EntitiesInit.HELPER_CHICKEN.get(),
                    Chicken.createAttributes().build()
            );
        }
    }

    /**
     * Forge-bus handler that prevents predators (foxes, wolves, ocelots, etc.)
     * from acquiring the helper chicken as a target. The chicken is invulnerable
     * via {@link HelperChickenEntity#hurt}, but vanilla still drives the predator
     * toward it and plays the eat animation, which is jarring during onboarding.
     * Cancel-on-target nips that off at the source.
     */
    @EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
    public static class ForgeBus {
        @SubscribeEvent
        public static void onTargetChange(LivingChangeTargetEvent event) {
            if (event.getNewTarget() instanceof HelperChickenEntity) {
                event.setCanceled(true);
            }
        }
    }
}
