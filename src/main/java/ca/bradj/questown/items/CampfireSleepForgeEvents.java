package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CampfireSleepForgeEvents {

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!CampfireSleepHandler.isCampfireSleeper(sp.getUUID())) {
            return;
        }
        CampfireSleepHandler.onWake(sp);
        applyGrogginess(sp);
        AdvancementsInit.TUTORIAL_TRIGGER.trigger(sp, TutorialTrigger.Triggers.FirstCampfireSleep);
    }

    private static void applyGrogginess(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 6000, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, false));
    }
}
