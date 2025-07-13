package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.AddWorkFromUIMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.CONFIRMED;
import static ca.bradj.questown.core.network.AddWorkFromUIMessage.Action.REJECTED;
import static ca.bradj.questown.gui.PagedCardScreen.*;

public class ItemJobsScreen extends Screen {

    public static final int EXTRA_HEIGHT = 56;
    private final List<UIJob> jobs;
    private final PagedCardScreen<UIJob> delegate;
    private final Ingredient requestedItem;
    private final BlockPos flagPos;

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
        AddWorkFromUIMessage m = new AddWorkFromUIMessage(requestedItem, flagPos, CONFIRMED);
        QuestownNetwork.CHANNEL.sendToServer(m);
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
        this.addRenderableWidget(
                new Button(
                        pageLeftX + BIG_PADDING,
                        pageTopY + delegate.backgroundHeight - buttonHeight - BIG_PADDING,
                        backgroundWidth - (2 * BIG_PADDING),
                        buttonHeight,
                        Compat.translatable("menu.item_jobs.add_to_work_requests"),
                        (p_96776_) -> this.send()
                )
        );
    }

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.renderBackground(stack);
        this.delegate.renderBg(stack, partialTicks, mouseX, mouseY);
        super.render(stack, mouseX, mouseY, partialTicks);

        String renderString = "Jobs:";
        ImmutableRect2i textArea = getTitleArea();
        Compat.drawLightText(font, stack, renderString, textArea.getX(), textArea.getY());
        Ingredients.render(
                itemRenderer,
                requestedItem,
                textArea.getX() + textArea.getWidth() - 16,
                textArea.getY() - 6
        );
        this.delegate.afterRender(stack, font, mouseX, mouseY, partialTicks, jobs, false);
        renderTooltips(stack, mouseX, mouseY, textArea.expandBy(4));
    }

    @Override
    public boolean mouseClicked(
            double x,
            double y,
            int p_94697_
    ) {
        @NotNull ImmutableRect2i ta = getTitleArea();
        if (UtilClean.isCoordInBox(x, y, ta.getX(), ta.getY(), ta.getWidth(), ta.getHeight())) {
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
            PoseStack stack,
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
            fill(
                    stack,
                    textArea.getX(),
                    textArea.getY(),
                    textArea.getX() + textArea.getWidth(),
                    textArea.getY() + textArea.getHeight(),
                    0x30000000
            );
            renderTooltip(
                    stack,
                    ImmutableList.of(
                            Compat.translatable("menu.item_jobs.title_tooltip"),
                            Ingredients.getName(requestedItem)
                    ),
                    Optional.empty(),
                    mouseX,
                    mouseY
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
            PoseStack stack,
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

        renderJobTitle(stack, d);

        TriFunction<CardCoordinates, List<Ingredient>, Boolean, List<Component>> renderStrip = (cc, ings, shiftRight) ->
                RenderUtil.stripOfRequestableItems(
                        (ing, coord) -> Ingredients.render(itemRenderer, ing, coord.x(), coord.y()),
                        (ing) -> ImmutableList.of(Ingredients.getName(ing)),
                        (text, coord) -> Compat.drawDarkText(font, stack, text, coord.x(), coord.y()),
                        (topLeft, botRight) -> RenderUtil.highlight(stack, topLeft, botRight),
                        ings,
                        shiftRight ? cc.topLeft().shifted(64, 0) : cc.topLeft(),
                        cc.bottomRight(),
                        mouse
                );

        Component itemsText = Compat.translatable("menu.item_jobs.items_used");

        int labelOffset = 5;

        Compat.drawDarkText(font, stack, itemsText, x, (c = down2.apply(c)).topY() + labelOffset);

        List<Component> tt1 = renderStrip.apply(c, d.ingredients(), true);
        c = c.shiftedDown(font.lineHeight);

        Component toolsText = Compat.translatable("menu.item_jobs.tools_used");
        Compat.drawDarkText(font, stack, toolsText, x, (c = down.apply(c)).topY() + labelOffset);
        List<Component> tt2 = renderStrip.apply(c, d.tools(), true);
        c = c.shiftedDown(font.lineHeight);

        Component producesText = Compat.translatable("menu.item_jobs.produces");
        Compat.drawDarkText(font, stack, producesText, x, (c = down.apply(c)).topY() + labelOffset);
        List<Ingredient> v = Ingredients.fromItems(d.result());
        List<Component> tt3 = renderStrip.apply(c, putRequestedItemFirst(v), true);
        c = c.shiftedDown(font.lineHeight);

        Component roomName = Compat.translatable("room." + d.roomNameTranslationKey().getPath());
        Component translatable = Compat.translatable("menu.item_jobs.room", roomName);
        Compat.drawDarkText(font, stack, translatable, x, (c = down.apply(c)).topY());
        List<Component> tt4 = renderStrip.apply(scootch.apply(c), d.roomRecipe(), false);

        int bgX = (width - backgroundWidth) / 2;
        int bgY = (height - delegate.backgroundHeight) / 2;
        int stripHeight = Util.faceWidth * 3;
        int stripY = bgY + delegate.backgroundHeight - stripHeight - buttonHeight - 4 - (BIG_PADDING * 2);
        fill(stack, bgX, stripY, bgX + backgroundWidth, stripY + stripHeight - 2, RenderUtil.SHADOW);
        int i = 0;
        for (UUID uuid : d.villagersWhoCanDoJob()) {
            int stripOffset = (stripHeight - (Util.faceWidth * 2)) / 2;
            stripOffset -= 1;
            blitFace(stack, bgX + Util.faceWidth - 3, stripY + stripOffset, uuid, i++);
        }
        return UtilClean.lastNonNull(tt1, tt2, tt3, tt4);
    }

    private List<Ingredient> putRequestedItemFirst(List<Ingredient> v) {
        if (Ingredients.isTag(requestedItem)) {
            return v;
        }
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();
        b.add(requestedItem);
        v.stream().filter(z -> !Ingredients.equal(z, requestedItem)).forEach(b::add);
        return b.build();
    }

    private void renderJobTitle(
            PoseStack stack,
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
        Compat.drawDarkText(font, stack, jobName, textArea.getX(), textArea.getY());
    }

    private static void blitFace(
            PoseStack stack,
            int x,
            int y,
            UUID uuid,
            int i
    ) {
        Util.blitFace(stack, uuid, x + (Util.faceWidth * 4 * i), y, 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        return this.delegate.getExtraAreas();
    }
}