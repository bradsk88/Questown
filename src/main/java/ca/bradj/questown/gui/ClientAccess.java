package ca.bradj.questown.gui;

import ca.bradj.questown.commands.JobArgument;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
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
            Collection<JobID> unlockedJobs,
            Collection<JobID> unlockableJobs,
            JobID currentJob,
            boolean showBlockOfProgressTab
    ) {
        return openScreen(() -> new VillagerAdvancementsScreen(
                flagPos,
                villagerUUID,
                unlockedJobs,
                unlockableJobs,
                currentJob,
                showBlockOfProgressTab
        ));
    }

    public static void openWorkRequestConfirm(
            Ingredient itemRequested,
            Map<JobID, ResourceLocation> iconsForJobsWhichProduceResult,
            BlockPos flagPos
    ) {
        openScreen(() -> new WorkRequestConfirmScreen(itemRequested, iconsForJobsWhichProduceResult, flagPos));
    }

    public static void openItemJobs(
            Ingredient requestedItem,
            Collection<UIJob> jobs,
            BlockPos flagPos
    ) {
        openScreen(() -> new ItemJobsScreen(requestedItem, jobs, flagPos));
    }

    public static void syncJobsForCommands(
            ImmutableList<JobID> jobs
    ) {
        JobArgument.jobIDs = jobs;
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
