package ca.bradj.questown.gui;

import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class ClientAccess {
    public static boolean openVillagerAdvancements(
            BlockPos flagPos,
            UUID villagerUUID,
            JobID currentJob
    ) {
        Minecraft.getInstance().setScreen(new VillagerAdvancementsScreen(
                flagPos, villagerUUID, currentJob
        ));
        return true;
    }
    public static void showHint(Component c) {
        Minecraft.getInstance().gui.setOverlayMessage(c, false);
    }
}
