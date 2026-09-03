package ca.bradj.questown.gui;

import ca.bradj.questown.QT;
import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.CONFIRMED;
import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.REJECTED;
import static ca.bradj.questown.gui.PagedCardScreen.*;

public class ItemJobsScreen extends Screen {

    public static final int EXTRA_HEIGHT = 56;
    private final List<UIJob> jobs;
    private final PagedCardScreen<UIJob> delegate;
    private @NotNull Ingredient requestedItem;
    private final BlockPos flagPos;
    private Button addButton;

    public ItemJobsScreen(
            Ingredient requestedItem,
            Collection<UIJob> jobs,
            BlockPos flagPos
    ) {
        super(Compat.translatable("menu.work_add_confirm.title"));

        this.delegate = new PagedCardScreen<>(
                () -> height,
                () -> width,
                () -> ImmutableList.copyOf(jobs),
                UtilClean::noOpConsumer,
                this::renderCardContent,
                3,
                16,
                EXTRA_HEIGHT
        );
        this.jobs = ImmutableList.copyOf(jobs);
        this.requestedItem = requestedItem;
        this.flagPos = flagPos;

    }

    private void send() {
        ItemStack first = getFirst();
        if (first.isEmpty()) {
            QT.GUI_LOGGER.error("Cannot add work request for job with empty result");
            return;
        }
        AddWorkFromUIMessage m = new AddWorkFromUIMessage(Ingredient.of(first), flagPos, CONFIRMED);
        QuestownNetwork.CHANNEL.sendToServer(m);
    }

    private ItemStack getFirst() {
        UIJob j = jobs.get(delegate.getCurrentPageIndex());
        ImmutableList<ItemStack> r = j.result();
        if (r.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack first = r.get(0);
        if (first.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return first;
    }

    @Override
    public boolean keyReleased(
            int keyCode,
            int scanCode,
            int modifiers
    ) {
        if (keyCode == GLFW.GLFW_KEY_Q) { // TODO: Get from user's config
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void init() {
        super.init();
        this.delegate.afterInit(this::addRenderableWidget);
        int pageTopY = ((this.height - delegate.backgroundHeight)) / 2;
        int pageLeftX = ((this.width - backgroundWidth) / 2);
        int buttonHeight = (2 * font.lineHeight) + 2;
        this.addButton = this.addRenderableWidget(
                Button.builder(
                        Compat.translatable("menu.item_jobs.add_to_work_requests"),
                        (p_96776_) -> this.send()
                ).bounds(
                        pageLeftX + BIG_PADDING,
                        pageTopY + delegate.backgroundHeight - buttonHeight - BIG_PADDING,
                        backgroundWidth - (2 * BIG_PADDING),
                        buttonHeight
                ).build()
        );
    }

    @Override
    public void render(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(gui);
        this.addButton.visible = !getFirst().isEmpty();
        this.delegate.renderBg(gui, partialTicks, mouseX, mouseY);
        super.render(gui, mouseX, mouseY, partialTicks);

        String renderString = "Jobs:";
        ImmutableRect2i textArea = getTitleArea();
        Compat.drawLightText(font, gui, renderString, textArea.getX(), textArea.getY());
        Ingredients.render(
                gui,
                requestedItem,
                textArea.getX() + textArea.getWidth() - 16,
                textArea.getY() - 6
        );
        this.delegate.afterRender(gui, font, mouseX, mouseY, partialTicks, jobs, false);
        renderTooltips(gui, mouseX, mouseY, textArea.expandBy(4));
    }

    @Override
    public boolean mouseClicked(
            double x,
            double y,
            int p_94697_
    ) {
        @NotNull ImmutableRect2i ta = getTitleArea();
        Supplier<Boolean> in = () -> UtilClean.isCoordInBox(x, y, ta.getX(), ta.getY(), ta.getWidth(), ta.getHeight());
        if (in.get() && !requestedItem.isEmpty()) {
            AddWorkFromUIMessage m = new AddWorkFromUIMessage(requestedItem, flagPos, REJECTED);
            QuestownNetwork.CHANNEL.sendToServer(m);
        }
        return super.mouseClicked(x, y, p_94697_);
    }

    private @NotNull ImmutableRect2i getTitleArea() {
        String sizeString = "Jobs:XXX";
        ImmutableRect2i pageArea = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
        pageArea = pageArea.moveUp(16);
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, sizeString);
        return textArea;
    }

    private void renderTooltips(
            GuiGraphics gui,
            int mouseX,
            int mouseY,
            ImmutableRect2i textArea
    ) {
        if (UtilClean.isCoordInBox(
                mouseX,
                mouseY,
                textArea.getX(),
                textArea.getY(),
                textArea.getWidth(),
                textArea.getHeight()
        )) {
            gui.fill(
                    textArea.getX(),
                    textArea.getY(),
                    textArea.getX() + textArea.getWidth(),
                    textArea.getY() + textArea.getHeight(),
                    0x30000000
            );
            this.setTooltipForNextRenderPass(
                    List.of(
                            Compat.translatable("menu.item_jobs.title_tooltip").getVisualOrderText(),
                            Ingredients.getName(requestedItem).getVisualOrderText()
                    )
            );
        }
    }

    @Override
    public boolean mouseScrolled(
            double p_94686_,
            double p_94687_,
            double p_94688_
    ) {
        return this.delegate.mouseScrolled(p_94686_, p_94687_, p_94688_, this::isMouseOver, this::mouseScrolled);
    }

    private List<Component> renderCardContent(
            GuiGraphics gui,
            Card<UIJob> card,
            Coordinate mouse
    ) {
        PagedCardScreen.CardCoordinates c = card.coords();
        UIJob d = card.data();

        int x = c.leftX();

        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> scootch = (cc) -> cc.shiftedDown(12);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down = (cc) -> cc.shiftedDown(16);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down2 = (cc) -> cc.shiftedDown(24);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down3 = (cc) -> cc.shiftedDown(30);

        renderJobTitle(gui, d);

        TriFunction<CardCoordinates, List<Ingredient>, Boolean, List<Component>> renderStrip = (cc, ings, shiftRight) ->
                RenderUtil.stripOfRequestableItems(
                        (ing, coord) -> Ingredients.render(gui, ing, coord.x(), coord.y()),
                        (ing) -> ImmutableList.of(Ingredients.getName(ing)),
                        (text, coord) -> Compat.drawDarkText(font, gui, text, coord.x(), coord.y()),
                        (topLeft, botRight) -> RenderUtil.highlight(gui, topLeft, botRight),
                        ings,
                        shiftRight ? cc.topLeft().shifted(64, 0) : cc.topLeft(),
                        cc.bottomRight(),
                        mouse
                );

        Component itemsText = Compat.translatable("menu.item_jobs.items_used");

        int labelOffset = 5;

        Compat.drawDarkText(font, gui, itemsText, x, (c = down2.apply(c)).topY() + labelOffset);

        List<Component> tt1 = renderStrip.apply(c, d.ingredients(), true);
        c = c.shiftedDown(font.lineHeight);

        Component toolsText = Compat.translatable("menu.item_jobs.tools_used");
        Compat.drawDarkText(font, gui, toolsText, x, (c = down.apply(c)).topY() + labelOffset);
        List<Component> tt2 = renderStrip.apply(c, d.tools(), true);
        c = c.shiftedDown(font.lineHeight);

        Component producesText = Compat.translatable("menu.item_jobs.produces");
        Compat.drawDarkText(font, gui, producesText, x, (c = down.apply(c)).topY() + labelOffset);
        List<Ingredient> v = Ingredients.fromItems(d.result());
        List<Component> tt3 = renderStrip.apply(c, putRequestedItemFirst(v), true);
        c = c.shiftedDown(font.lineHeight);

        Component roomName = Compat.translatable("room." + d.roomNameTranslationKey().getPath());
        Component translatable = Compat.translatable("menu.item_jobs.room", roomName);
        Compat.drawDarkText(font, gui, translatable, x, (c = down.apply(c)).topY());
        List<Component> tt4 = renderStrip.apply(scootch.apply(c), d.roomRecipe(), false);

        int bgX = (width - backgroundWidth) / 2;
        int bgY = (height - delegate.backgroundHeight) / 2;
        int stripHeight = Util.faceWidth * 3;
        int stripY = bgY + delegate.backgroundHeight - stripHeight - buttonHeight - 4 - (BIG_PADDING * 2);
        gui.fill(bgX, stripY, bgX + backgroundWidth, stripY + stripHeight - 2, RenderUtil.SHADOW);
        int i = 0;
        for (UUID uuid : d.villagersWhoCanDoJob()) {
            int stripOffset = (stripHeight - (Util.faceWidth * 2)) / 2;
            stripOffset -= 1;
            blitFace(gui, bgX + Util.faceWidth - 3, stripY + stripOffset, uuid, i++);
        }
        return UtilClean.lastNonNull(tt1, tt2, tt3, tt4);
    }

    private List<Ingredient> putRequestedItemFirst(List<Ingredient> v) {
        if (Ingredients.isTag(requestedItem)) {
            return v;
        }
        if (requestedItem.isEmpty()) {
            return v;
        }
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        b.add(requestedItem);
        v.stream().filter(z -> !Ingredients.equal(z, requestedItem)).forEach(b::add);
        return b.build();
    }

    private void renderJobTitle(
            GuiGraphics gui,
            UIJob d
    ) {
        ImmutableRect2i pageArea = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
        pageArea = pageArea.moveDown(20);
        Component jobName = Compat.translatable(
                "menu.common.job_name",
                Jobs.getRootNameComponent(d.jobId()),
                Compat.literal(d.jobId().jobId())
        );
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, jobName);
        Compat.drawDarkText(font, gui, jobName, textArea.getX(), textArea.getY());
    }

    private static void blitFace(
            GuiGraphics gui,
            int x,
            int y,
            UUID uuid,
            int i
    ) {
        Util.blitFace(gui, uuid, x + (Util.faceWidth * 4 * i), y, 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        return this.delegate.getExtraAreas();
    }
}
