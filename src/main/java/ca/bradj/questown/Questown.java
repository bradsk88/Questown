package ca.bradj.questown;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.ForgeRenderTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Questown.MODID)
public class Questown {
    public static final String MODID = "questown";

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger();

    public Questown() {
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        TilesInit.TILES.register(bus);
        BlocksInit.BLOCKS.register(bus);
        ItemsInit.register(bus);
    }

    private void setup(final FMLCommonSetupEvent event) {
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(BlocksInit.TOWN_FLAG.get(), RenderType.cutout());
    }

}
