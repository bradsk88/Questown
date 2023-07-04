package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mobs.visitor.QTEntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DataSerializerEntry;

@Mod.EventBusSubscriber(modid = Questown.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DataSerializersInit {


    @SubscribeEvent
    public static void registerDataSerializers(
            RegistryEvent.Register<DataSerializerEntry> event
    ) {
        ResourceLocation islName = new ResourceLocation(Questown.MODID, "item_stack_list");
        event.getRegistry().register(
                new DataSerializerEntry(QTEntityDataSerializers.ITEM_STACK_LIST).setRegistryName(islName)
        );
    }

}