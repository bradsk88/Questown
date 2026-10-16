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
import java.util.IdentityHashMap;
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
            boolean showBlockOfProgressTab,
            boolean showInventoryTab
    ) {
        return openScreen(() -> new VillagerAdvancementsScreen(
                flagPos,
                villagerUUID,
                unlockedJobs,
                unlockableJobs,
                currentJob,
                showBlockOfProgressTab,
                showInventoryTab
        ));
    }

    public static void openWorkRequestConfirm(
            Ingredient itemRequested,
            Map<JobID, ResourceLocation> iconsForJobsWhichProduceResult,
            BlockPos flagPos
    ) {
        openScreen(() -> new WorkRequestConfirmScreen(itemRequested, iconsForJobsWhichProduceResult, flagPos));
    }

    public static void openRelocationConfirm(
            BlockPos targetPos,
            int farFixtureCount
    ) {
        openScreen(() -> new RelocationConfirmScreen(targetPos, farFixtureCount));
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

    /**
     * Vanilla's TutorialToast stays on screen until the player right-clicks a block -
     * "Your camp is ready" was still showing after the first campfire sleep
     * (2026-10-15 playtest). Each toast gets a wall-clock deadline instead.
     */
    private static final long TUTORIAL_TOAST_LIFETIME_MS = 20_000L;
    @Nullable
    private static net.minecraft.client.gui.components.toasts.TutorialToast activeTutorialToast;
    private static final Map<net.minecraft.client.gui.components.toasts.TutorialToast, Long> toastDeadlines =
            new IdentityHashMap<>();

    public static void showTutorialToast(Component title, Component description) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.gui.components.toasts.TutorialToast toast =
                new net.minecraft.client.gui.components.toasts.TutorialToast(
                        net.minecraft.client.gui.components.toasts.TutorialToast.Icons.RIGHT_CLICK,
                        title, description, false
                );
        activeTutorialToast = toast;
        toastDeadlines.put(toast, System.currentTimeMillis() + TUTORIAL_TOAST_LIFETIME_MS);
        mc.getToasts().addToast(toast);
    }

    /**
     * Hides each toast once its deadline passes. Called from the client tick so the
     * cap holds even when no screen dismisses the toast.
     */
    public static void tickTutorialToasts() {
        long now = System.currentTimeMillis();
        toastDeadlines.forEach((toast, deadline) -> {
            if (now >= deadline) {
                toast.hide();
            }
        });
        toastDeadlines.entrySet().removeIf(e -> e.getValue() <= now);
        if (activeTutorialToast != null && !toastDeadlines.containsKey(activeTutorialToast)) {
            activeTutorialToast = null;
        }
    }

    public static void dismissTutorialToast() {
        if (activeTutorialToast != null) {
            activeTutorialToast.hide();
            activeTutorialToast = null;
        }
        toastDeadlines.clear();
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
