package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

public class VillagerTabs extends Tabs implements SubUI {

    public VillagerTabs(
            @Nullable Runnable invScreenFn,
            @Nullable Runnable qScreenFn,
            @Nullable Runnable sScreenFn
    ) {
        super(ImmutableList.of(
                new Tab(
                        (stack, x, y) -> {
                        }, // TODO: Render icon
                        setScreen(invScreenFn),
                        "tooltips.inventory",
                        invScreenFn == null
                ),
                new Tab(
                        (stack, x, y) -> {
                        }, // TODO: Render icon
                        setScreen(qScreenFn),
                        "tooltips.quests",
                        qScreenFn == null
                ),
                new Tab(
                        (stack, x, y) -> {
                            int txBefore = RenderSystem.getShaderTexture(0);
                            RenderSystem.setShaderTexture(0, new ResourceLocation("textures/gui/icons.png"));
                            GuiComponent.blit(stack, x + 13, y + 11, 0, 0, 15, 9, 9, 256, 256);
                            RenderSystem.setShaderTexture(0, txBefore);
                        },
                        setScreen(sScreenFn),
                        "tooltips.stats",
                        sScreenFn == null
                )
        ));
    }

    private static Runnable setScreen(Runnable s) {
        if (s == null) {
            return () -> {
            };
        }
        return s;
    }

    public static Runnable makeOpenFn(
            BlockPos fp,
            UUID gathererId,
            String type
    ) {
        Runnable fn = () -> QuestownNetwork.CHANNEL.sendToServer(new OpenVillagerMenuMessage(
                fp.getX(), fp.getY(), fp.getZ(),
                gathererId, type
        ));
        return fn;
    }

}
