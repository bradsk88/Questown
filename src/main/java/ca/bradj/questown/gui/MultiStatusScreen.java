package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MultiStatusScreen extends AbstractPagedCardScreen<MultiStatusMenu, UUID> {

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
        super(
                menu, new Inventory(null) {
                    @Override
                    public Component getDisplayName() {
                        return Compat.literal("");
                    }
                }, Compat.literal("")
        );
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
    protected ImmutableList<UUID> cardsData() {
        return ImmutableList.copyOf(syncedData.villagers.keySet());
    }

    @Override
    protected void renderCardContent(
            PoseStack poseStack,
            Card<UUID> card,
            int mouseX,
            int mouseY
    ) {
        renderStatus(poseStack, card.coords(), card.data());
        renderInventory(card.coords(), card.data());
        renderFace(poseStack, card.coords(), card.data());
    }

    private void renderInventory(
            CardCoordinates coords,
            UUID uuid
    ) {
        int iconX = coords.leftXPadded();
        Collection<net.minecraft.world.item.Item> items = syncedData.items.get(uuid);
        for (Item item : items) {
            int destX = iconX + SMALL_PADDING;
            int destY = coords.topYPadded() + BIG_PADDING;
            this.itemRenderer.renderAndDecorateItem(new ItemStack(item), destX, destY);
            iconX += 16;
        }
    }

    private void renderFace(
            PoseStack stack,
            CardCoordinates coords,
            UUID uuid
    ) {
        int x = coords.leftXPadded();
        int destY = coords.topYPadded();
        Util.blitFace(stack, uuid, x, destY);
        int nameX = x + Util.faceWidth + 4;
        Compat.drawDarkText(
                font,
                stack,
                Compat.translatable(syncedData.villagers.get(uuid).a().rootId()),
                nameX,
                destY
        );
    }

    private void renderStatus(
            PoseStack stack,
            CardCoordinates coords,
            UUID uuid
    ) {
        int drawWidth = 32;
        int drawHeight = 32;
        int texWidth = 32;
        int texHeight = 32;

        int destX = coords.rightXPadded() - drawWidth;
        int destY = coords.topYPadded() - MED_PADDING;
        IStatus<?> status = getSmoothedStatus(uuid);
        JobID job = syncedData.villagers.get(uuid).a();
        ResourceLocation texture = ServerJobsRegistry.getTexture(job, status);
        RenderSystem.setShaderTexture(0, texture);
        blit(stack, destX, destY, 0, 0, drawWidth, drawHeight, texWidth, texHeight);
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

        bgY = bgY + BIG_PADDING + BIG_PADDING; // Accounting for pager
        int leftX = bgX + backgroundWidth - 16 - 32;
        int topY = bgY + 16;
        int rightX = leftX + 32;
        int botY = topY + 32;

        ImmutableList<UUID> uuids = cardsData();
        for (int i = 0; i < uuids.size(); i++) {
            if (mouseX < leftX) {
                continue;
            }
            if (mouseX > rightX) {
                continue;
            }
            if (mouseY < topY + (i * CARD_HEIGHT)) {
                continue;
            }
            if (mouseY > botY + (i * CARD_HEIGHT)) {
                continue;
            }
            UUID villagerUUID = uuids.get(i);
            IStatus<?> status = getSmoothedStatus(villagerUUID);
            JobID jobId = syncedData.villagers().get(villagerUUID).a();
            ImmutableList<Component> components = JobTooltips.get(status, jobId);
            super.renderTooltip(stack, components, Optional.empty(), mouseX, mouseY);
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