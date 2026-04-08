package ca.bradj.questown.items;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.ClientAccess;
import ca.bradj.questown.mc.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CampfireSleepClientEvents {

    private static int hintCooldown = 0;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (hintCooldown-- > 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof TownWand)) {
            return;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) {
            return;
        }
        BlockState campfireState = mc.level.getBlockState(bhr.getBlockPos());
        if (!campfireState.is(Blocks.CAMPFIRE)) {
            return;
        }

        boolean lit = campfireState.hasProperty(CampfireBlock.LIT) && campfireState.getValue(CampfireBlock.LIT);
        String key;
        if (!lit) {
            key = "message.wand.campfire.not_lit";
        } else if (mc.level.isNight()) {
            key = "message.wand.campfire.sleep_hint";
        } else {
            key = "message.wand.campfire.day_hint";
        }
        ClientAccess.showHint(Compat.translatable(key));
        hintCooldown = 40;
    }
}
