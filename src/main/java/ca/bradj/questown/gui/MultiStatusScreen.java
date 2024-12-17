package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.Internal;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import mezz.jei.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.elements.GuiIconButtonSmall;
import mezz.jei.gui.textures.Textures;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MultiStatusScreen extends AbstractContainerScreen<MultiStatusMenu> {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;

    private static final int CARD_PADDING = 1;
    private static final int PAGE_PADDING = 10;
    private static final int CARD_WIDTH = (backgroundWidth) - (PAGE_PADDING * 2);
    private static final int CARD_HEIGHT = 42;
    private static final int MAX_CARDS_PER_PAGE = (backgroundHeight - PAGE_PADDING) / (CARD_HEIGHT + CARD_PADDING);

    private final DrawableNineSliceTexture background;

    private final Map<UUID, Collection<IStatus<?>>> statusSmoothingQueue = new HashMap<>();
    private final FlagTabs tabs;
    private final JEI.NineNine cardBackground;

    private final GuiIconButtonSmall nextPage;
    private final GuiIconButtonSmall previousPage;
    private static final int buttonWidth = 13;
    private static final int buttonHeight = 13;
    private static final int buttonPadding = 6;
    private int currentPage = 0;

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
        Textures textures = Internal.getTextures();
        this.background = textures.getRecipeGuiBackground();
        this.tabs = FlagTabs.forMenu(menu);
        this.cardBackground = JEI.getRecipeBackground();

        IDrawableStatic arrowNext = JEI.getArrowNext();
        IDrawableStatic arrowPrevious = JEI.getArrowPrevious();

        this.nextPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowNext, b -> nextPage()
        );
        this.previousPage = JEI.guiIconButtonSmall(
                0, 0, buttonWidth, buttonHeight, arrowPrevious, b -> previousPage()
        );
    }

    @Override
    protected void init() {
        int y = (this.height - backgroundHeight) / 2;
        int pageStringY = y + buttonPadding;
        int x = ((this.width - backgroundWidth) / 2);
        this.previousPage.x = x + buttonPadding;
        this.previousPage.y = pageStringY;
        this.nextPage.x = x + backgroundWidth - buttonWidth - buttonPadding;
        this.nextPage.y = pageStringY;
        this.addRenderableWidget(this.previousPage);
        this.addRenderableWidget(this.nextPage);
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
        // Render the page buttons
        this.previousPage.render(stack, mouseX, mouseY, partialTicks);
        this.nextPage.render(stack, mouseX, mouseY, partialTicks);
        this.renderTooltip(stack, mouseX, mouseY);
    }

    private void nextPage() {
        int totalPages = (int) Math.ceil((double) syncedData.villagers.size() / MAX_CARDS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            currentPage++;
        }
    }

    private void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
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

        renderPageNum(stack, bgX);

        bgY = bgY + PAGE_PADDING + PAGE_PADDING;
        renderCardsBG(stack, bgY, bgX);

        Iterable<UUID> uuids = getUuids();

        renderStatus(stack, bgX, bgY, uuids);
        renderInventory(bgX, bgY,uuids);
        renderFaces(stack, bgX, bgY, uuids);
    }

    private @NotNull Iterable<UUID> getUuids() {
        Iterable<UUID> uuids = Iterables.skip(syncedData.villagers.keySet(), currentPage * MAX_CARDS_PER_PAGE);
        uuids = Iterables.limit(uuids, MAX_CARDS_PER_PAGE);
        return uuids;
    }

    private void renderPageNum(
            PoseStack poseStack,
            int x
    ) {
        // Draw page numbers
        fill(
                poseStack,
                x + buttonPadding + buttonWidth,
                nextPage.y,
                x + backgroundWidth - buttonPadding - buttonWidth,
                nextPage.y + buttonHeight,
                0x30000000
        );
        int totalPages = (int) Math.ceil((double) syncedData.villagers.size() / MAX_CARDS_PER_PAGE);
        String pageString = "Page " + (currentPage + 1) + " / " + totalPages;

        ImmutableRect2i pageArea = MathUtil.union(previousPage.getArea(), nextPage.getArea());
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, pageString);
        Compat.drawLightText(font, poseStack, pageString, textArea.getX(), textArea.getY());
    }

    private void renderCardsBG(
            PoseStack stack,
            int bgY,
            int bgX
    ) {
        int startIndex = currentPage * MAX_CARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + MAX_CARDS_PER_PAGE, syncedData.villagers.size());

        int x = bgX + PAGE_PADDING;
        int y = bgY + PAGE_PADDING;

        for (int i = startIndex; i < endIndex; i++) {
            int row = i - startIndex;
            int cardY = y + row * (CARD_HEIGHT + CARD_PADDING);
            this.cardBackground.draw(stack, x, cardY, CARD_WIDTH, CARD_HEIGHT);
        }
    }

    private void renderInventory(int x, int y, Iterable<UUID> uuids) {
        x += 16;
        for (UUID uuid : uuids) {
            int iconX = x - 12;
            Collection<net.minecraft.world.item.Item> items = syncedData.items.get(uuid);
            for (Item item : items) {
                this.itemRenderer.renderAndDecorateItem(new ItemStack(item), iconX += 16 + 4, y + 32);
            }
            y += CARD_HEIGHT + CARD_PADDING;
        }
    }

    private void renderFaces(
            PoseStack stack,
            int x, int y,
            Iterable<UUID> uuids
    ) {
        float texStartX = 8;
        float texStartY = 8;
        int texFileWidth = 64;
        int texFileHeight = 64;
        int drawNumPixelsX = 8;
        int drawNumPixelsY = 8;
        x += 16;
        for (UUID uuid : uuids) {
            ResourceLocation texture = VisitorMobRenderer.getTextureLocation(uuid);
            RenderSystem.setShaderTexture(0, texture);
            blit(stack, x, y + 20, texStartX, texStartY, drawNumPixelsX, drawNumPixelsY, texFileWidth, texFileHeight);
            int nameX = x + drawNumPixelsX + 4;
            Compat.drawDarkText(
                    font,
                    stack,
                    Compat.translatable(syncedData.villagers.get(uuid).a().rootId()),
                    nameX,
                    y + 20
            );
            y += CARD_HEIGHT + CARD_PADDING;
        }
    }

    private void renderStatus(
            PoseStack stack,
            int x, int y,
            Iterable<UUID> uuids
    ) {

        int drawWidth = 32;
        int drawHeight = 32;
        int texWidth = 32;
        int texHeight = 32;

        int destX = x + backgroundWidth - 16 - 32;
        int destY = y + CARD_PADDING;

        for (UUID uuid : uuids) {
            IStatus<?> status = getSmoothedStatus(uuid);
            JobID job = syncedData.villagers.get(uuid).a();
            ResourceLocation texture = JobsRegistry.getTexture(job, status);
            RenderSystem.setShaderTexture(0, texture);
            blit(stack, destX, destY + 14, 0, 0, drawWidth, drawHeight, texWidth, texHeight);
            destY = destY + CARD_HEIGHT + CARD_PADDING;
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

        bgY = bgY + PAGE_PADDING + PAGE_PADDING; // Accounting for pager
        int leftX = bgX + backgroundWidth - 16 - 32;
        int topY = bgY + 16;
        int rightX = leftX + 32;
        int botY = topY + 32;
        int texWidth = 32;
        int texHeight = 32;

        ImmutableList<UUID> uuids = ImmutableList.copyOf(getUuids());
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

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(
                new Rect2i(x, y, backgroundWidth, backgroundHeight)
        );
    }
}