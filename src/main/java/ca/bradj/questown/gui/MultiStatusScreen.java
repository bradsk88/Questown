package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.EvictingQueue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MultiStatusScreen extends AbstractContainerScreen<MultiStatusMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final DrawableNineSliceTexture background;

    private final Map<UUID, Collection<IStatus<?>>> statusSmoothingQueue = new HashMap<>();

    public record SyncedData(
            Map<UUID, UtilClean.Pair<JobID, IStatus<?>>> villagers
    ) {
    }

    // TODO: These are updated by a network message. Is there any way we can protect access?
    public static SyncedData syncedData = new SyncedData(
            new HashMap<>()
    );

    public MultiStatusScreen(
            MultiStatusMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, Compat.literal(""));
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
    }

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        super.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        this.renderTooltip(stack, mouseX, mouseY);
    }

    @Override
    protected void renderBg(
            PoseStack stack,
            float partialTicks,
            int mouseX,
            int mouseY
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        this.background.draw(stack, bgX, bgY, backgroundWidth, backgroundHeight);
        renderStatus(stack);
        renderInventory(stack);
    }

    private void renderInventory(PoseStack stack) {
        // TODO: render held items (not as slots, read-only)
//        int x = (this.width - backgroundWidth) / 2;
//        int y = (this.height - backgroundHeight) / 2;
//        int yCoord = 0;
//        for (int i = 0; i < menu.slots.size(); i++) {
//            Slot s = menu.slots.get(i);
//            int xCoord = x - 1 + s.x;
//            yCoord = y - 1 + s.y;
//            this.slot.draw(stack, xCoord, yCoord);
//        }
//        int iconX = x - 12;
//        for (Ingredient i : ClientJobWantedResources.wantedIngredients) {
//            int curSeconds = (int) (System.currentTimeMillis() / 1000);
//            ItemStack[] matchingStacks = i.getItems();
//            ItemStack itemStack = matchingStacks[curSeconds % matchingStacks.length];
//            this.itemRenderer.renderAndDecorateItem(itemStack, iconX += 16 + 4, yCoord + 32);
//        }
    }

    private void renderStatus(
            PoseStack stack
    ) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;

        int drawWidth = 32;
        int drawHeight = 32;
        int texWidth = 32;
        int texHeight = 32;

        int destX = x + backgroundWidth - 16 - 32;
        int destY = y + 16;

        for (UUID uuid : syncedData.villagers.keySet()) {
            ResourceLocation texture = StatusArt.getTexture(syncedData.villagers.get(uuid).a(), getSmoothedStatus(uuid));
            RenderSystem.setShaderTexture(0, texture);
            blit(stack, destX, destY, 0, 0, drawWidth, drawHeight, texWidth, texHeight);
            destY = destY + drawHeight;
        }
    }

    private @NotNull IStatus<?> getSmoothedStatus(UUID villagerUUID) {
        Collection<IStatus<?>> q = UtilClean.getOrDefaultCollection(
                statusSmoothingQueue,
                villagerUUID,
                EvictingQueue.create(5),
                true
        );
        q.add(syncedData.villagers.get(villagerUUID).b());
        statusSmoothingQueue.put(villagerUUID, q);
        HashMap<IStatus<?>, Integer> counter = new HashMap<>();
        for (IStatus<?> iStatus : q) {
            counter.compute(iStatus, (ignored, oldCt) -> oldCt == null ? 1 : oldCt + 1);
        }
        return counter
                .entrySet()
                .stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElseThrow();
    }

    @Override
    protected void renderTooltip(
            @NotNull PoseStack stack,
            int mouseX,
            int mouseY
    ) {
//        // TODO: Implement
//        int x = (this.width - backgroundWidth) / 2;
//        int y = (this.height - backgroundHeight) / 2;
//        int leftX = x + backgroundWidth - 16 - 32;
//        int topY = y + 16;
//        int rightX = leftX + 32;
//        int botY = topY + 32;
//
//        String jobId = menu.getRootJobId();
//        TranslatableComponent jobName = new TranslatableComponent("jobs." + jobId);
//
//        if (this.tabs.renderTooltip(
//                x, y, mouseX, mouseY,
//                key -> super.renderTooltip(stack, new TranslatableComponent(key), mouseX, mouseY)
//        )) {
//            return;
//        }
//
//        if (this.tabs.renderTooltip(
//                x, y, mouseX, mouseY,
//                key -> super.renderTooltip(stack, Compat.translatable(key), mouseX, mouseY)
//        )) {
//            return;
//        }
//
//        if (mouseX > leftX && mouseX < rightX) {
//            if (mouseY > topY && mouseY < botY) {
//                // TODO: Render root AND current job
//                IStatus<?> status = getSmoothedStatus();
//                @Nullable String cat = status.getCategoryId();
//                if (cat == null) {
//                    cat = jobId;
//                }
//
//                // TODO: Handle work seeker statuses some where else
//                if (WorkSeekerJob.isSeekingWork(menu.jobId)) {
//                    cat = "work_seeker";
//                }
//                if (
//                        DinerNoTableWork.isDining(menu.jobId) ||
//                                DinerWork.isDining(menu.jobId) ||
//                                DinerRawFoodWork.isDining(menu.jobId)
//                ) {
//                    cat = "diner";
//                }
//
//                TranslatableComponent component = new TranslatableComponent(
//                        String.format("tooltips.villagers.job.%s.status_1.%s", cat, status.nameV2()),
//                        jobName
//                );
//                TranslatableComponent component2 = new TranslatableComponent(
//                        String.format("tooltips.villagers.job.%s.status_2.%s", cat, status.nameV2()),
//                        jobName
//                );
//                super.renderTooltip(stack, ImmutableList.of(component, component2), Optional.empty(), mouseX, mouseY);
//                return;
//            }
//        }
//
//        int yCoord = y - 1;
//        for (
//                int i = 0; i < menu.slots.size(); i++) {
//            Slot s = menu.slots.get(i);
//            int xCoord = x - 1 + s.x;
//            yCoord = y - 1 + s.y;
//            if (i >= TE_INVENTORY_FIRST_SLOT_INDEX) {
//                if (renderLocksTooltip(stack, xCoord + 1, yCoord + 16 + 2, mouseX, mouseY)) {
//                    return;
//                }
//            }
//        }
//
//        int iconX = x - 12;
//        for (
//                Ingredient i : ClientJobWantedResources.wantedIngredients) {
//            if (renderNeedsTooltip(stack, iconX += 16 + 4, yCoord + 32, mouseX, mouseY, i)) {
//                return;
//            }
//        }
//
//        super.
//
//                renderTooltip(
//                        stack,
//                        mouseX,
//                        mouseY
//                );
    }
//
//    private boolean renderNeedsTooltip(
//            @NotNull PoseStack stack,
//            int leftX,
//            int topY,
//            int mouseX,
//            int mouseY,
//            Ingredient item
//    ) {
//        int rightX = leftX + 16;
//        int botY = topY + 16;
//        if (mouseX > leftX && mouseX < rightX) {
//            if (mouseY > topY && mouseY < botY) {
//                TranslatableComponent jPart = new TranslatableComponent(String.format("jobs.%s", menu.getRootJobId()));
//                TranslatableComponent component = new TranslatableComponent(
//                        "tooltips.villagers.job.needs", jPart, Ingredients.getName(item)
//                );
//                super.renderTooltip(stack, ImmutableList.of(component), Optional.empty(), mouseX, mouseY);
//                return true;
//            }
//        }
//        return false;
//    }
}