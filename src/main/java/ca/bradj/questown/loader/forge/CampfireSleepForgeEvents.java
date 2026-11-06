package ca.bradj.questown.loader.forge;

import ca.bradj.questown.mc.Compat;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.items.CampfireSleepHandler;
import ca.bradj.questown.QT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class CampfireSleepForgeEvents {

    private static long heartbeatTick = -1;

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

    // Dev diagnostics: while anyone is campfire-sleeping, log the sleepers and the time every 60 ticks.
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) { if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) { return; }
        if (CampfireSleepHandler.getCampfireSleeperCount() == 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        long time = server.overworld().getGameTime();
        if (time % 60L != 0) {
            return;
        }
        if (time == heartbeatTick) {
            return;
        }
        heartbeatTick = time;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        int sleeping = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (CampfireSleepHandler.isCampfireSleeper(p.getUUID()) && p.isSleeping()) {
                sleeping++;
            }
        }
        QT.JOB_LOGGER.dev(
                "[campfire-sleep] heartbeat: {} campfire-sleeper(s), {} vanilla-sleeping, dayTime={} isNight={}",
                CampfireSleepHandler.getCampfireSleeperCount(), sleeping,
                overworld != null ? (overworld.getDayTime() % 24000L) : -1,
                overworld != null && overworld.isNight()
        );
    }

    private static void applyGrogginess(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 6000, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, false));
        // The effects have no icon, so say why the player feels weak.
        player.displayClientMessage(Compat.translatable("message.questown.campfire_sleep.groggy"), false);
    }
}
