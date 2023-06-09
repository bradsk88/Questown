package ca.bradj.questown;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.core.init.*;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.QuestsScreen;
import ca.bradj.questown.gui.VisitorDialogScreen;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
        Questown.LOGGER.info("Main load");
        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        BlocksInit.BLOCKS.register(bus);
        TilesInit.TILES.register(bus);
        ItemsInit.register(bus);
        MenuTypesInit.register(bus);
        EntitiesInit.register(bus);
        RewardsInit.register(bus);
        ScheduleInit.register(bus);
        AdvancementsInit.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC, Config.FILENAME);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RecipeItemConfig.SPEC, RecipeItemConfig.FILENAME);
    }

    private void setup(final FMLCommonSetupEvent event) {
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        Questown.LOGGER.info("Doing client stuff");
        ItemBlockRenderTypes.setRenderLayer(BlocksInit.COBBLESTONE_TOWN_FLAG.get(), RenderType.cutout());
        MenuScreens.register(MenuTypesInit.TOWN_QUESTS.get(), QuestsScreen::new);
        MenuScreens.register(MenuTypesInit.VISITOR_QUESTS.get(), VisitorDialogScreen::new);
        Questown.LOGGER.info("Registered screens");
        event.enqueueWork(() -> {
            EntityRenderers.register(
                    EntitiesInit.VISITOR.get(),
                    VisitorMobRenderer::new
            );
        });
    }

}
