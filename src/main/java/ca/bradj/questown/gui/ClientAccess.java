package ca.bradj.questown.gui;

import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import ca.bradj.questown.jobs.JobID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ClientAccess {
    public static boolean openVillagerAdvancements(
            BlockPos flagPos,
            UUID villagerUUID,
            JobID currentJob
    ) {
        return openScreen(() -> new VillagerAdvancementsScreen(flagPos, villagerUUID, currentJob));
    }

    public static void openWorkRequestConfirm(
            ResourceLocation itemRequested,
            Map<JobID, ResourceLocation> iconsForJobsWhichProduceResult,
            BlockPos flagPos
    ) {
        openScreen(() -> new WorkRequestConfirmScreen(itemRequested, iconsForJobsWhichProduceResult, flagPos));
    }

    public static void openItemJobs(
            Ingredient requestedItem,
            Collection<UIJob> jobs
    ) {
        openScreen(() -> new ItemJobsScreen(requestedItem, jobs));
    }

    public static boolean openScreen(Supplier<Screen> screen) {
        Minecraft.getInstance().setScreen(screen.get());
        return true;
    }

    public static void showHint(Component c) {
        Minecraft.getInstance().gui.setOverlayMessage(c, false);
    }

    public static void closeScreens() {
        Minecraft.getInstance().setScreen(null);
    }
}
