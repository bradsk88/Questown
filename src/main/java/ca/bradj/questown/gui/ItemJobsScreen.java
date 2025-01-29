package ca.bradj.questown.gui;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.util.MathUtil;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.item.crafting.Ingredient;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static ca.bradj.questown.gui.PagedCardScreen.backgroundWidth;

public class ItemJobsScreen extends Screen {

    private final List<UIJob> jobs;
    private final PagedCardScreen<UIJob> delegate;
    private final Ingredient requestedItem;

    public ItemJobsScreen(
            Ingredient requestedItem,
            Collection<UIJob> jobs
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
                32
        );
        this.jobs = ImmutableList.copyOf(jobs);
        this.requestedItem = requestedItem;
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

        String sizeString = "Jobs:XXX";
        String renderString = "Jobs:";
        ImmutableRect2i pageArea = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
        pageArea = pageArea.moveUp(16);
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, sizeString);
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

    private void renderTooltips(
            PoseStack stack,
            int mouseX,
            int mouseY,
            ImmutableRect2i textArea
    ) {
        if (UtilClean.mouseInBox(
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

    private void renderCardContent(
            PoseStack stack,
            PagedCardScreen.Card<UIJob> card,
            UtilClean.Pair<Integer, Integer> mouse
    ) {
        PagedCardScreen.CardCoordinates c = card.coords();
        UIJob d = card.data();

        int x = c.leftX();
        int i = 0;

        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down = (cc) -> cc.shiftedDown(16);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down2 = (cc) -> cc.shiftedDown(font.lineHeight);
        Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down3 = (cc) -> cc.shiftedDown(30);

        renderJobTitle(stack, d);

        // TODO: Translate
        Compat.drawDarkText(font, stack, Compat.literal("Items"), x, (c = down3.apply(c)).topY());
        c = renderItems(d.ingredients(), c, i, down2);
        if (d.ingredients().isEmpty()) {
            c = down2.apply(c);
        }

        c = c.shiftedDown(font.lineHeight);

        Compat.drawDarkText(font, stack, Compat.literal("Tools"), x, (c = down.apply(c)).topY());
        i = 0;
        c = renderItems(d.tools(), c, i, down2);
        if (d.tools().isEmpty()) {
            c = down2.apply(c);
        }

        c = c.shiftedDown(font.lineHeight);

        TranslatableComponent translatable = Compat.translatable(
                "menu.item_jobs.room",
                Compat.translatable("room." + d.roomNameTranslationKey().getPath())
        );
        Compat.drawDarkText(font, stack, translatable, x, (c = down.apply(c)).topY());
        i = 0;
        c = renderItems(d.roomRecipe(), c, i, down2);
        if (d.roomRecipe().isEmpty()) {
            c = down2.apply(c);
        }

        int bgX = (width - backgroundWidth) / 2;
        int bgY = (height - delegate.backgroundHeight) / 2;
        int stripHeight = Util.faceWidth * 3;
        int stripY = bgY + delegate.backgroundHeight - stripHeight;
        fill(stack, bgX, bgY + delegate.backgroundHeight, bgX + backgroundWidth, stripY, 0x30000000);
        i = 0;
        for (UUID uuid : d.villagersWhoCanDoJob()) {
            int stripOffset = (stripHeight - (Util.faceWidth * 2)) / 2;
            stripOffset -= 1;
            blitFace(stack, bgX + Util.faceWidth, stripY + stripOffset, uuid, i++);
        }
    }

    private void renderJobTitle(
            PoseStack stack,
            UIJob d
    ) {
        ImmutableRect2i pageArea = MathUtil.union(delegate.previousPage.getArea(), delegate.nextPage.getArea());
        pageArea = pageArea.moveDown(20);
        TranslatableComponent jobName = Compat.translatable(
                "menu.common.job_name",
                Jobs.getRootNameComponent(d.jobId()),
                Compat.literal(d.jobId().jobId())
        );
        ImmutableRect2i textArea = MathUtil.centerTextArea(pageArea, font, jobName);
        Compat.drawDarkText(font, stack, jobName, textArea.getX(), textArea.getY());
    }

    private PagedCardScreen.CardCoordinates renderItems(
            ImmutableList<Ingredient> d,
            PagedCardScreen.CardCoordinates c,
            int i,
            Function<PagedCardScreen.CardCoordinates, PagedCardScreen.CardCoordinates> down2
    ) {
        c = down2.apply(c);
        for (Ingredient ing : d) {
            Ingredients.render(itemRenderer, ing, c.leftX() + (i++ * 24), c.topY());
            // TODO: Hihlight and tooltip
        }
        return c;
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