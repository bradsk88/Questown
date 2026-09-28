package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.advancements.TutorialTrigger;
import ca.bradj.questown.core.init.AdvancementsInit;
import ca.bradj.questown.QT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME)
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

    // Diagnostic heartbeat for the "two players sleeping, morning never comes"
    // class of bug. Every 60 ticks while anyone is campfire-sleeping, log the
    // sleeper count, how many vanilla still counts as sleeping, and the day
    // time. This tells us which of the three failure modes we are in:
    //   - heartbeat stops  -> the server thread hung (a true deadlock)
    //   - heartbeat keeps running and dayTime advances but no SleepFinishedTimeEvent
    //     fires -> the night is simply not skipping (deep-sleep condition unmet)
    //   - SleepFinishedTimeEvent fires but the server then goes silent
    //     -> the wake path (onMorning / onWake) is the stall
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
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
        QT.JOB_LOGGER.info(
                "[campfire-sleep] heartbeat: {} campfire-sleeper(s), {} vanilla-sleeping, dayTime={} isNight={}",
                CampfireSleepHandler.getCampfireSleeperCount(), sleeping,
                overworld != null ? (overworld.getDayTime() % 24000L) : -1,
                overworld != null && overworld.isNight()
        );
    }

    private static void applyGrogginess(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 6000, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 6000, 0, false, false));
    }
}
