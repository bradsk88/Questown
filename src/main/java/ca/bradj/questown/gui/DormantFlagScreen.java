package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.network.DormantFlagChoiceMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Shown when a player right-clicks a dormant town flag with no deed waiting (ADR-0009 loss
 * protection, dev ruling 2026-10-23). Before this, waking a packed town or replacing a lost deed
 * needed {@code /qt flag <pos> shutdown wake|reissue}. Three choices:
 * <ul>
 *   <li><b>Wake the town</b>: the town unpacks here ({@code wakeInPlace}).</li>
 *   <li><b>Get the deed back</b>: a new deed appears above the flag ({@code reissueDeed}).</li>
 *   <li><b>Cancel</b>: close, nothing sent.</li>
 * </ul>
 * Flow layout: the message wraps to the panel width and the buttons sit below its real height.
 */
public class DormantFlagScreen extends CompatScreen {

    private static final int backgroundWidth = 176;
    private static final int PAGE_PADDING = 10;
    private static final int BUTTON_GAP = 4;

    private final JEI.NineNine background;
    private final BlockPos flagPos;

    private int panelHeight;
    private int pageLeftX;
    private int pageTopY;
    private int messageY;

    public DormantFlagScreen(BlockPos flagPos) {
        super(Compat.translatable("menu.dormant_flag.title"));
        this.background = JEI.getRecipeGuiBackground();
        this.flagPos = flagPos;
    }

    private Component message() {
        return Compat.translatable("menu.dormant_flag.message");
    }

    private int textWidth() {
        return backgroundWidth - (2 * PAGE_PADDING);
    }

    private int buttonHeight() {
        return (2 * font.lineHeight) + 2;
    }

    @Override
    protected void init() {
        // darkTextWrap spaces wrapped lines at 1.5x the font height (Compat.drawTextWrap).
        int messageHeight = Compat.splitText(font, message(), textWidth()).size() * (int) (font.lineHeight * 1.5);
        int buttonsHeight = 3 * (buttonHeight() + BUTTON_GAP);
        this.panelHeight = PAGE_PADDING + (2 * font.lineHeight) + messageHeight + PAGE_PADDING + buttonsHeight + PAGE_PADDING;
        this.pageLeftX = (this.width - backgroundWidth) / 2;
        this.pageTopY = ((this.height - panelHeight) / 2) + PAGE_PADDING;
        this.messageY = pageTopY + (2 * font.lineHeight);

        int y = messageY + messageHeight + PAGE_PADDING;
        addButton(Compat.translatable("menu.dormant_flag.wake"), b -> choose(b, true), y);
        y += buttonHeight() + BUTTON_GAP;
        addButton(Compat.translatable("menu.dormant_flag.reissue"), b -> choose(b, false), y);
        y += buttonHeight() + BUTTON_GAP;
        addButton(Compat.translatable("menu.common.cancel"), b -> this.onClose(), y);
    }

    private void addButton(Component label, Button.OnPress onPress, int y) {
        this.addRenderableWidget(Compat.button(
                label, onPress, pageLeftX + PAGE_PADDING, y, textWidth(), buttonHeight()
        ));
    }

    private void choose(Button b, boolean wake) {
        // A fast double-click must not send twice (docs/ui-principles.md 10).
        b.active = false;
        QuestownNetwork.sendToServer(new DormantFlagChoiceMessage(flagPos, wake));
        this.onClose();
    }

    @Override
    protected void renderPrologue(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        ctx.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
    }

    @Override
    protected void renderScreenSurface(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int y = (this.height - panelHeight) / 2;
        ctx.drawNineNine(this.background, pageLeftX, y, backgroundWidth, panelHeight);
    }

    @Override
    protected void renderScreenContent(RenderContext ctx, int mouseX, int mouseY, float partialTicks) {
        int textX = pageLeftX + PAGE_PADDING;
        ctx.darkText(title, textX, pageTopY);
        ctx.darkTextWrap(new Coordinate(textX, messageY), textWidth(), message());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<Rect2i> getExtraAreas() {
        int y = (this.height - panelHeight) / 2;
        return ImmutableList.of(new Rect2i(pageLeftX, y, backgroundWidth, panelHeight));
    }
}
