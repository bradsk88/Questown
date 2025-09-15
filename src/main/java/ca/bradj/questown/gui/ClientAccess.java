package ca.bradj.questown.gui;

import ca.bradj.questown.commands.JobArgument;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.gui.villager.advancements.VillagerAdvancementsScreen;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class ClientAccess {

    private static final Map<Pair<JobID, ProductionStatus>, ResourceLocation> artOverrides = new HashMap<>();
    private static final Map<Pair<JobID, ProductionStatus>, ImmutableList<Component>> textOverrides = new HashMap<>();

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

    public static void storeArtOverride(
            JobID id,
            ProductionStatus status,
            ResourceLocation artID
    ) {
        artOverrides.put(new Pair<>(id, status), artID);
    }

    public static ResourceLocation getArt(JobID id, ProductionStatus status) {
        return UtilClean.getOrDefault(artOverrides, new Pair<>(id, status), StatusArt.ERROR_TEX);
    }

    public static void storeTextOverride(
            ProductionStatus status,
            StatusPacket packet
    ) {
        textOverrides.put(new Pair<>(packet.jobId(), status), packet.texts());
    }

    public static @Nullable ImmutableList<Component> getStatusText(
            JobID jobId,
            ProductionStatus status
    ) {
        return UtilClean.getOrDefault(textOverrides, new Pair<>(jobId, status), null);
    }
}
