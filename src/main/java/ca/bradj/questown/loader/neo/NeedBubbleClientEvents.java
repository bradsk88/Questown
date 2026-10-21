package ca.bradj.questown.loader.neo;

import ca.bradj.questown.mobs.visitor.NeedBubbleFocus;
import ca.bradj.questown.Questown;
import ca.bradj.questown.render.BubbleRenderer;
import ca.bradj.questown.town.rooms.AttentionBubbles;
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
