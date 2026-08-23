package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import ca.bradj.questown.render.BubbleRenderer;
import ca.bradj.questown.town.rooms.DoorTrouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives {@link NeedBubbleFocus} once per client tick, and draws the dead-door bubble. */
@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NeedBubbleClientEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        NeedBubbleFocus.tick(Minecraft.getInstance());
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
