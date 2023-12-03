package ca.bradj.questown.integration.jei;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.*;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@JeiPlugin
public class QuestownJei implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Questown.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(QuestsScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(QuestsScreen containerScreen) {
                return containerScreen.getExtraAreas();
            }

            @Override
            public @Nullable Object getIngredientUnderMouse(
                    QuestsScreen containerScreen,
                    double mouseX,
                    double mouseY
            ) {
                ItemStack is = containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY);
                return is;
            }
        });
        registration.addGuiContainerHandler(QuestRemoveConfirmScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(QuestRemoveConfirmScreen containerScreen) {
                return containerScreen.getExtraAreas();
            }

            @Override
            public @Nullable Object getIngredientUnderMouse(
                    QuestRemoveConfirmScreen containerScreen,
                    double mouseX,
                    double mouseY
            ) {
                ItemStack is = containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY);
                return is;
            }
        });
        registration.addGuiContainerHandler(WorkScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(WorkScreen containerScreen) {
                return containerScreen.getExtraAreas();
            }

            @Override
            public @Nullable Object getIngredientUnderMouse(
                    WorkScreen containerScreen,
                    double mouseX,
                    double mouseY
            ) {
                ItemStack is = containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY);
                return is;
            }
        });
        registration.addGuiContainerHandler(AddWorkScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AddWorkScreen containerScreen) {
                return containerScreen.getExtraAreas();
            }

            @Override
            public @Nullable Object getIngredientUnderMouse(
                    AddWorkScreen containerScreen,
                    double mouseX,
                    double mouseY
            ) {
                ItemStack is = containerScreen.getHoveredIngredient((int) mouseX, (int) mouseY);
                return is;
            }
        });
        registration.addGuiContainerHandler(VisitorDialogScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(VisitorDialogScreen containerScreen) {
                return containerScreen.getExtraAreas();
            }
        });
    }
}
