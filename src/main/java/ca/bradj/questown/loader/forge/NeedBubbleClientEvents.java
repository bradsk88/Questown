package ca.bradj.questown.loader.forge;

import ca.bradj.questown.mobs.visitor.NeedBubbleFocus;
import ca.bradj.questown.Questown;
import ca.bradj.questown.render.BubbleRenderer;
import ca.bradj.questown.town.rooms.AttentionBubbles;
import ca.bradj.questown.town.rooms.DoorTrouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Drives {@link NeedBubbleFocus} once per client tick, and draws the dead-door bubble. */
@EventBusSubscriber(modid = Questown.MODID, bus = EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NeedBubbleClientEvents {

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) { if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) { return; }
        NeedBubbleFocus.tick(Minecraft.getInstance());
        ca.bradj.questown.mobs.helperchicken.ChickenArcHintFocus.tick(Minecraft.getInstance());
    }

    /**
     * Townie bubbles ride the entity renderer, but a dead door has no entity — its bubble is
     * drawn here, once, for whichever door (if any) currently holds the focus. Same single
     * bubble, same renderer, same 16-block gate. The "!" attention bubbles (flag with
     * incomplete quests, job board with no work requests) draw through the same single
     * focus — exactly one of a door or an attention target can hold it at a time.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        BlockPos door = NeedBubbleFocus.currentDoor();
        AttentionBubbles.Target attention = NeedBubbleFocus.currentAttention();
        if (door != null) {
            BubbleRenderer.renderIconBubbleAtBlock(
                    door,
                    DoorTrouble.sharedIcon(),
                    event.getPoseStack(),
                    buffer,
                    event.getCamera().getPosition()
            );
        } else if (attention != null) {
            BubbleRenderer.renderTextureBubbleAtBlock(
                    attention.pos(),
                    attention.kind().texture,
                    event.getPoseStack(),
                    buffer,
                    event.getCamera().getPosition()
            );
        }
        buffer.endBatch();
    }

    private NeedBubbleClientEvents() {
    }
}
