package ca.bradj.questown;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.core.init.*;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkHooks;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Questown.MODID)
public class Questown {
    public static final String MODID = "questown";

    // Directly reference a log4j logger.
    /**
     * @deprecated Use QT.LOGGER
     */
    public static final Logger LOGGER = QT.LOGGER;
    public static final Item.Properties DEFAULT_ITEM_PROPS = new Item.Properties().
            tab(ModItemGroup.QUESTOWN_GROUP);

    public Questown() {
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
        CommandsInit.register(bus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC, Config.FILENAME);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RecipeItemConfig.SPEC, RecipeItemConfig.FILENAME);
    }

    public static ResourceLocation ResourceLocation(String id) {
        return new ResourceLocation(Questown.MODID, id);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(QuestownNetwork::init);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        MenuScreens.register(MenuTypesInit.TOWN_QUESTS.get(), QuestsScreen::forTown);
        MenuScreens.register(MenuTypesInit.TOWN_QUESTS_REMOVE.get(), QuestRemoveConfirmScreen::new);
        MenuScreens.register(MenuTypesInit.TOWN_WORK.get(), WorkScreen::new);
        MenuScreens.register(MenuTypesInit.VISITOR_QUESTS.get(), VisitorDialogScreen::new);
        MenuScreens.register(MenuTypesInit.GATHERER_INVENTORY.get(), InventoryAndStatusScreen::new);
        MenuScreens.register(MenuTypesInit.VILLAGER_STATS.get(), VillagerStatsScreen::new);
        event.enqueueWork(() -> EntityRenderers.register(
                EntitiesInit.VISITOR.get(),
                VisitorMobRenderer::new
        ));
    }
}
