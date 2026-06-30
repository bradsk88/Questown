package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.core.network.RelocationChoiceMessage;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.JEI;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The "some of your town is too far away to come with you" confirmation shown when a relocation deed
 * is placed far from part of the town (ADR-0009, #199 Phase 4). Three choices, mirroring the
 * server-side {@link ca.bradj.questown.town.entity.TownRelocation.FarFixturePolicy}:
 * <ul>
 *   <li><b>Bring it anyway</b> — carry every fixture ({@code BRING_ALL}).</li>
 *   <li><b>Leave it behind</b> — drop the out-of-range fixtures ({@code LEAVE_BEHIND}).</li>
 *   <li><b>Cancel</b> — abort; the deed is never consumed (no packet, just close).</li>
 * </ul>
 * Bring/leave send a {@link RelocationChoiceMessage} that finishes the placement on the server.
 */
public class RelocationConfirmScreen extends Screen {

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

        this.addRenderableWidget(new Button(
                buttonX, firstButtonY, buttonWidth, buttonHeight,
                Compat.translatable("menu.relocation_confirm.bring_anyway"),
                (b) -> choose(false)
        ));
        this.addRenderableWidget(new Button(
                buttonX, firstButtonY + buttonHeight + 4, buttonWidth, buttonHeight,
                Compat.translatable("menu.relocation_confirm.leave_behind"),
                (b) -> choose(true)
        ));
        this.addRenderableWidget(new Button(
                buttonX, firstButtonY + (2 * (buttonHeight + 4)), buttonWidth, buttonHeight,
                Compat.translatable("menu.common.cancel"),
                (b) -> this.onClose()
        ));
    }

    private void choose(boolean leaveBehind) {
        QuestownNetwork.CHANNEL.sendToServer(new RelocationChoiceMessage(targetPos, leaveBehind));
        this.onClose();
    }

    @Override
    public void render(
            PoseStack stack,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        this.fillGradient(stack, 0, 0, this.width, this.height, -1072689136, -804253680);
        this.renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);
        int textX = pageLeftX + PAGE_PADDING;
        Compat.drawDarkText(this.font, stack, title, textX, pageTopY);
        Compat.drawDarkText(
                this.font, stack,
                Compat.translatable("menu.relocation_confirm.too_far"),
                textX, messageY
        );
        Compat.drawDarkText(
                this.font, stack,
                Compat.translatable("menu.relocation_confirm.fixture_count", farFixtureCount),
                textX, messageY + (font.lineHeight * 2)
        );
    }

    @Override
    public void renderBackground(PoseStack poseStack) {
        int x = (this.width - backgroundWidth) / 2;
        int y = (this.height - backgroundHeight) / 2;
        this.background.draw(poseStack, x, y, backgroundWidth, backgroundHeight);
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
