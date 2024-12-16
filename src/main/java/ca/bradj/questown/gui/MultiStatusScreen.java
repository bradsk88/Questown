package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.mc.Compat;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MultiStatusScreen extends AbstractContainerScreen<MultiStatusMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private final DrawableNineSliceTexture background;

    private final Map<UUID, Collection<IStatus<?>>> statusSmoothingQueue = new HashMap<>();
    private final FlagTabs tabs;

    public record SyncedData(
            Map<UUID, UtilClean.Pair<JobID, IStatus<?>>> villagers,
            Map<UUID, ImmutableList<net.minecraft.world.item.Item>> items
    ) {
    }

    // TODO: These are updated by a network message. Is there any way we can protect access?
    public static SyncedData syncedData = new SyncedData(
            new HashMap<>(),
            new HashMap<>()
    );

    public MultiStatusScreen(
            MultiStatusMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, new Inventory(null) {
            @Override
            public Component getDisplayName() {
                return Compat.literal("");
            }
        }, Compat.literal(""));
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.tabs = FlagTabs.forMenu(menu);
    }

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        super.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        tabs.draw(new RenderContext(itemRenderer, stack), bgX, bgY);
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
        renderInventory();
    }

    private void renderInventory() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        y += 32;
        x += 8;
        for (UUID uuid : syncedData.items.keySet()) {
            int iconX = x - 12;
            Collection<net.minecraft.world.item.Item> items = syncedData.items.get(uuid);
            for (Item item : items) {
                this.itemRenderer.renderAndDecorateItem(new ItemStack(item), iconX += 16 + 4, y);
            }
            y += 32;
        }
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
            ResourceLocation texture = StatusArt.getTexture(
                    syncedData.villagers.get(uuid).a(),
                    getSmoothedStatus(uuid)
            );
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
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;

        if (this.tabs.renderTooltip(
                bgX, bgY, mouseX, mouseY,
                key -> super.renderTooltip(stack, Compat.translatable(key), mouseX, mouseY)
        )) {
            return;
        }
        int leftX = bgX + backgroundWidth - 16 - 32;
        int topY = bgY + 16;
        int rightX = leftX + 32;
        int botY = topY + 32;
        int texWidth = 32;
        int texHeight = 32;

        ImmutableList<UUID> uuids = ImmutableList.copyOf(syncedData.villagers.keySet());
        for (int i = 0; i < uuids.size(); i++) {
            if (mouseX < leftX) {
                continue;
            }
            if (mouseX > rightX) {
                continue;
            }
            if (mouseY < topY + (i * texHeight)) {
                continue;
            }
            if (mouseY > botY + (i * texHeight)) {
                continue;
            }
//                // TODO: Render root AND current job
            UUID villagerUUID = uuids.get(i);
            IStatus<?> status = getSmoothedStatus(villagerUUID);
            @Nullable String cat = status.getCategoryId();
            JobID jobId = syncedData.villagers().get(villagerUUID).a();
            if (cat == null) {
                cat = jobId.rootId();
            }

            // TODO: Handle work seeker statuses some where else
            if (WorkSeekerJob.isSeekingWork(jobId)) {
                cat = "work_seeker";
            }
            if (
                    DinerNoTableWork.isDining(jobId) ||
                            DinerWork.isDining(jobId) ||
                            DinerRawFoodWork.isDining(jobId)
            ) {
                cat = "diner";
            }

            TranslatableComponent jobName = new TranslatableComponent("jobs." + jobId.rootId());
            TranslatableComponent component = new TranslatableComponent(
                    String.format("tooltips.villagers.job.%s.status_1.%s", cat, status.nameV2()),
                    jobName
            );
            TranslatableComponent component2 = new TranslatableComponent(
                    String.format("tooltips.villagers.job.%s.status_2.%s", cat, status.nameV2()),
                    jobName
            );
            super.renderTooltip(stack, ImmutableList.of(component, component2), Optional.empty(), mouseX, mouseY);
            return;
        }
        super.renderTooltip(stack, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(
            double p_97748_,
            double p_97749_,
            int p_97750_
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight) / 2;
        tabs.mouseClicked(bgX, bgY, p_97748_, p_97749_);
        return super.mouseClicked(p_97748_, p_97749_, p_97750_);
    }
}