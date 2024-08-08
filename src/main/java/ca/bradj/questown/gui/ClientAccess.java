package ca.bradj.questown.gui;

import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

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
    public static void showWandHint(BlockPos bp) {
        Minecraft.getInstance().gui.setOverlayMessage(Compat.translatable(
                "message.wand.clicked_away", bp.getX(), bp.getY(), bp.getZ()
        ), false);
    }
}
