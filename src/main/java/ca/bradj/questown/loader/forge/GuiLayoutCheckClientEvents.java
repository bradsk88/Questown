package ca.bradj.questown.loader.forge;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.GuiLayoutCapture;
import ca.bradj.questown.gui.GuiLayoutLinter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only client adapter that validates Questown screen layouts and logs violations.
 *
 * <p>On a screen's first render, {@code Compat.drawDarkTextWrap} records each text block's bounds;
 * {@link GuiLayoutLinter} checks those with the widgets, so text that overruns into the next
 * element shows as an overlap. Runs once per screen open; inert in shipped jars
 * ({@link FMLEnvironment#production}).
 */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GuiLayoutCheckClientEvents {

    private static final String QUESTOWN_GUI_PACKAGE = "ca.bradj.questown.gui.";
    private static boolean pendingCheck = false;

    private GuiLayoutCheckClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        pendingCheck = !FMLEnvironment.production && isQuestownScreen(event.getScreen());
    }

    @SubscribeEvent
    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (pendingCheck && isQuestownScreen(event.getScreen())) {
            GuiLayoutCapture.begin();
        }
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        if (!pendingCheck || !GuiLayoutCapture.isActive()) {
            return;
        }
        pendingCheck = false;
        Screen screen = event.getScreen();
        List<GuiLayoutLinter.Box> boxes = new ArrayList<>(collectWidgetBoxes(screen));
        boxes.addAll(GuiLayoutCapture.end());
        report(screen, GuiLayoutLinter.check(screen.width, screen.height, boxes));
    }

    private static boolean isQuestownScreen(Screen screen) {
        return screen.getClass().getName().startsWith(QUESTOWN_GUI_PACKAGE);
    }

    private static List<GuiLayoutLinter.Box> collectWidgetBoxes(Screen screen) {
        Font font = Minecraft.getInstance().font;
        List<GuiLayoutLinter.Box> boxes = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget)) {
                continue;
            }
            int contentWidth = widget instanceof Button ? font.width(widget.getMessage()) : -1;
            boxes.add(new GuiLayoutLinter.Box(
                    labelOf(widget), widget.x, widget.y, widget.getWidth(), widget.getHeight(), contentWidth));
        }
        return boxes;
    }

    private static String labelOf(AbstractWidget widget) {
        return widget.getMessage() == null ? "widget:?" : "widget:'" + widget.getMessage().getString() + "'";
    }

    private static void report(Screen screen, List<GuiLayoutLinter.Violation> violations) {
        if (violations.isEmpty()) {
            QT.LOGGER.info("[gui-lint] {} ({}x{}) — OK, no layout violations",
                    screen.getClass().getSimpleName(), screen.width, screen.height);
            return;
        }
        QT.LOGGER.warn("[gui-lint] {} ({}x{}) — {} layout violation(s):",
                screen.getClass().getSimpleName(), screen.width, screen.height, violations.size());
        for (GuiLayoutLinter.Violation v : violations) {
            QT.LOGGER.warn("[gui-lint]   {}", v);
        }
    }
}
