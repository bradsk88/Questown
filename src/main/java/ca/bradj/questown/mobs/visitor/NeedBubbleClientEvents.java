package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.render.BubbleRenderer;
import ca.bradj.questown.town.rooms.DoorTrouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Drives {@link NeedBubbleFocus} once per client tick, and draws the dead-door bubble. */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class NeedBubbleClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        NeedBubbleFocus.tick(Minecraft.getInstance());
        ca.bradj.questown.mobs.helperchicken.ChickenArcHintFocus.tick(Minecraft.getInstance());
    }

    /**
     * Townie bubbles ride the entity renderer, but a dead door has no entity — its bubble is
     * drawn here, once, for whichever door (if any) currently holds the focus. Same single
     * bubble, same renderer, same 16-block gate.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        BlockPos door = NeedBubbleFocus.currentDoor();
        if (door == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        BubbleRenderer.renderIconBubbleAtBlock(
                door,
                DoorTrouble.sharedIcon(),
                event.getPoseStack(),
                buffer,
                event.getCamera().getPosition()
        );
        buffer.endBatch();
    }

    private NeedBubbleClientEvents() {
    }
}
