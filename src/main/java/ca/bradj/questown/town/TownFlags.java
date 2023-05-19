package ca.bradj.questown.town;

import ca.bradj.questown.Questown;
import net.minecraftforge.event.world.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TownFlags {

    private static final Map<UUID, TownFlagBlockEntity> flags = new HashMap<>();

    static void register(
            UUID uuid,
            TownFlagBlockEntity entity
    ) {
        flags.put(uuid, entity);
    }

    @SubscribeEvent
    public static void OnWake(SleepFinishedTimeEvent event) {
        for (TownFlagBlockEntity e : flags.values()) {
            e.onMorning();
        }
    }

}
