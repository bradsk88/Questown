package ca.bradj.questown.town.econ;

import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.interfaces.TownInterface;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class Economics {
    public static void registerUnmetRoom(
            TownInterface town,
            UUID uuid,
            ResourceLocation room
    ) {
        long tick = Util.getTick(town.getServerLevel());
        town.getEconomicsHandle().registerUnmetRoom(tick, uuid, room.toString());
    }
}
