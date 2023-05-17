package ca.bradj.questown.core.advancements;

import ca.bradj.questown.Questown;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import vazkii.patchouli.api.PatchouliAPI;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AdvancementEvents {

    @SubscribeEvent
    public static void entityAttrEvent(AdvancementEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        if (!Questown.MODID.equals(event.getAdvancement().getId().getNamespace())) {
            return;
        }
        if (!"root".equals(event.getAdvancement().getId().getPath())) {
            return;
        }
        sp.sendMessage(new TranslatableComponent("messages.town_flag.first_visit_journal"), null);
        sp.addItem(PatchouliAPI.get().getBookStack(new ResourceLocation(Questown.MODID, "intro")));
    }

}
