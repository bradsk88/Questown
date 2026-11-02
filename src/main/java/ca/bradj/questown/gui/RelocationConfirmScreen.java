package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RelocationChoiceMessage;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;

import java.util.List;

/** Confirms placing a relocation deed when some of the town would be left too far away: bring it anyway, leave it behind, or cancel. */
public class RelocationConfirmScreen extends CompatScreen {

    private static final int backgroundWidth = 176;
    private static final int backgroundHeight = 166;
    private static final int PAGE_PADDING = 10;

    private final JEI.NineNine background;
    private final BlockPos targetPos;
    private final int farFixtureCount;

    private int pageTopY;
    private int pageLeftX;
    private int messageY;

    public RelocationConfirmScreen(
            BlockPos targetPos,
            int farFixtureCount
    ) {
        super(Compat.translatable("menu.relocation_confirm.title"));
        this.background = JEI.getRecipeGuiBackground();
        this.targetPos = targetPos;
        this.farFixtureCount = farFixtureCount;
    }

    @Override
    protected void init() {
        this.pageTopY = ((this.height - backgroundHeight) / 2) + PAGE_PADDING;
        this.pageLeftX = ((this.width - backgroundWidth) / 2);
        this.messageY = pageTopY + (2 * font.lineHeight);

        int buttonHeight = (2 * font.lineHeight) + 2;
        int buttonWidth = backgroundWidth - (2 * PAGE_PADDING);
        int buttonX = pageLeftX + PAGE_PADDING;
        int firstButtonY = pageTopY + backgroundHeight - (2 * PAGE_PADDING) - (3 * (buttonHeight + 4));

        this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.relocation_confirm.bring_anyway"),
                (b) -> choose(false),
                buttonX, firstButtonY, buttonWidth, buttonHeight
        ));
        this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.relocation_confirm.leave_behind"),
                (b) -> choose(true),
                buttonX, firstButtonY + buttonHeight + 4, buttonWidth, buttonHeight
        ));
        this.addRenderableWidget(Compat.button(
                Compat.translatable("menu.common.cancel"),
                (b) -> this.onClose(),
                buttonX, firstButtonY + (2 * (buttonHeight + 4)), buttonWidth, buttonHeight
        ));
    }

    private void choose(boolean leaveBehind) {
        QuestownNetwork.sendToServer(new RelocationChoiceMessage(targetPos, leaveBehind));
        this.onClose();
    }

    @Override
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        ctx.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
    }

    @Override
    protected void renderScreenSurface(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        ctx.drawNineNine(this.background, x, y, backgroundWidth, backgroundHeight);
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int textX = pageLeftX + PAGE_PADDING;
        ctx.darkText(title, textX, pageTopY);
        ctx.darkText(Compat.translatable("menu.relocation_confirm.too_far"), textX, messageY);
        ctx.darkText(
                Compat.translatable("menu.relocation_confirm.fixture_count", farFixtureCount),
                textX,
                messageY + (font.lineHeight * 2)
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        return ImmutableList.of(new Rect2i(x, y, backgroundWidth, backgroundHeight));
    }
}
