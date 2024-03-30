package ca.bradj.questown.gui;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

public class VillagerTabs extends Tabs implements SubUI {

    public VillagerTabs(
            @Nullable Runnable invScreenFn,
            @Nullable Runnable qScreenFn,
            @Nullable Runnable sScreenFn,
            @Nullable Runnable skillScreenFn
    ) {
        super(ImmutableList.of(
                new Tab(
                        (rc, x, y) -> rc.itemRenderer().renderAndDecorateItem(Items.CHEST.getDefaultInstance(), x + 10, y + 7),
                        setScreen(invScreenFn),
                        "tooltips.inventory",
                        invScreenFn == null
                ),
                new Tab(
                        (rc, x, y) -> {
                            int txBefore = RenderSystem.getShaderTexture(0);
                            RenderSystem.setShaderTexture(0, Questown.ResourceLocation("textures/menu/gatherer/menu.png"));
                            PoseStack stack = rc.stack();
                            stack.pushPose();
                            stack.scale(15f / 30f, 15f / 30f, 1);
                            int adjustedX = (int) ((x + 11) / (15f / 30f));
                            int adjustedY = (int) ((y + 7) / (15f / 30f));
                            GuiComponent.blit(stack, adjustedX, adjustedY, 0, 17, 7, 30, 30, 256, 256);
                            stack.popPose();

                            RenderSystem.setShaderTexture(0, txBefore);
                        },
                        setScreen(skillScreenFn),
                        "tooltips.skills",
                        skillScreenFn == null
                ),
                new Tab(
                        (rc, x, y) -> rc.itemRenderer().renderAndDecorateItem(Items.BOOK.getDefaultInstance(), x + 10, y + 7),
                        setScreen(qScreenFn),
                        "tooltips.quests",
                        qScreenFn == null
                ),
                new Tab(
                        (rc, x, y) -> {
                            int txBefore = RenderSystem.getShaderTexture(0);
                            RenderSystem.setShaderTexture(0, Questown.ResourceLocation("textures/menu/gatherer/menu.png"));
                            GuiComponent.blit(rc.stack(), x + 13, y + 11, 0, 0, 0, 9, 9, 256, 256);
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

    public static VillagerTabs forMenu(VillagerTabsEmbedding menu) {
        Collection<String> enabledTabs = menu.getEnabledTabs();

        Function<String, Runnable> factory = typ -> {
            if (enabledTabs.contains(typ)) {
                return makeOpenFn(menu.getFlagPos(), menu.getVillagerUUID(), typ);
            }
            return null;
        };
        return new VillagerTabs(
                factory.apply(OpenVillagerMenuMessage.INVENTORY),
                factory.apply(OpenVillagerMenuMessage.QUESTS),
                factory.apply(OpenVillagerMenuMessage.STATS),
                factory.apply(OpenVillagerMenuMessage.SKILLS)
        );
    }
}
