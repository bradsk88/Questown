package ca.bradj.questown;

import ca.bradj.questown.blocks.entity.renderer.AxeRackBlockEntityRenderer;
import ca.bradj.questown.blocks.entity.renderer.FoodDisplayEntityRenderer;
import ca.bradj.questown.blocks.entity.renderer.HospitalBedEntityRenderer;
import ca.bradj.questown.blocks.entity.renderer.PlateBlockEntityRenderer;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.QuestsScreen;
import ca.bradj.questown.gui.QuestRemoveConfirmScreen;
import ca.bradj.questown.gui.WorkScreen;
import ca.bradj.questown.gui.CreateStockRequestScreen;
import ca.bradj.questown.gui.VillagerStatsScreen;
import ca.bradj.questown.gui.VillagerEconomicsScreen;
import ca.bradj.questown.gui.TownEconomicsScreen;
import ca.bradj.questown.gui.JobUnlockConfirmScreen;
import ca.bradj.questown.gui.InventoryAndStatusScreen;
import ca.bradj.questown.gui.JobChangeConfirmScreen;
import ca.bradj.questown.gui.VillagerBlockofProgressScreen;
import ca.bradj.questown.gui.TownBlockofProgressScreen;
import ca.bradj.questown.gui.town.status.MultiStatusScreen;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.helperchicken.HelperChickenRenderer;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.Logger;

/**
 * Loader-neutral mod bootstrap.
 *
 * <p>The per-loader {@code @Mod} entry (see {@code loader.neo.QuestownMod}) installs the
 * {@link Loader} facade, registers the config specs, defines the registries, and wires the
 * setup/client events onto the active loader's buses. This class holds only the shared,
 * loader-independent pieces: the mod id, logging, default item props, and the neutral
 * {@code onCommonSetup}/{@code onClientSetup} bodies.
 */
public class Questown {
    public static final String MODID = "questown";

    // Directly reference a log4j logger.
    /**
     * @deprecated Use QT.LOGGER
     */
    public static final Logger LOGGER = QT.LOGGER;
    public static final Item.Properties DEFAULT_ITEM_PROPS = new Item.Properties();
    public static ResourceLocation ResourceLocationError = ResourceLocation("error");

    public static ResourceLocation ResourceLocation(String id) {
        return new ResourceLocation(Questown.MODID, id);
    }

    public static ResourceLocation ResourceLocation(String id, Object... args) {
        return new ResourceLocation(Questown.MODID, String.format(id, args));
    }

    // ---- Neutral bootstrap, driven by the per-loader @Mod entry -----------

    public static void registerConfigs() {
        Loader.registerConfig(Loader.ConfigType.SERVER, Config.SPEC, Config.FILENAME);
        Loader.registerConfig(Loader.ConfigType.SERVER, RecipeItemConfig.SPEC, RecipeItemConfig.FILENAME);
    }

    public static void onCommonSetup() {
        ca.bradj.questown.core.network.QuestownNetwork.init();
        ca.bradj.questown.jobs.declarative.DeclarativeJobs.staticInitialize();
        ca.bradj.questown.jobs.Events.onCommonSetup();
    }

    public static void onClientSetup() {
        Compat.setCutoutRenderType(ca.bradj.questown.core.init.BlocksInit.FALSE_DOOR_BLOCK.get());
        Compat.setCutoutRenderType(ca.bradj.questown.core.init.BlocksInit.FALSE_WALL_BLOCK.get());

        EntityRenderers.register(EntitiesInit.VISITOR.get(), VisitorMobRenderer::new);
        EntityRenderers.register(EntitiesInit.HELPER_CHICKEN.get(), HelperChickenRenderer::new);

        BlockEntityRenderers.register(TilesInit.PLATE.get(), PlateBlockEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.FOOD_DISPLAY.get(), FoodDisplayEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.AXE_RACK.get(), AxeRackBlockEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.HOSPITAL_BED.get(), HospitalBedEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.TOWN_FLAG.get(), ca.bradj.questown.blocks.entity.renderer.TownFlagDeedRenderer::new);
    }

    /**
     * Menu-screen captures, invoked from the per-loader {@code @Mod} constructor on the client.
     * NeoForge fires its menu-screen registration event at client init, before any mod-bus setup
     * event, so the captures must exist before then: see {@code NeoLoader#onRegisterMenuScreens}.
     */
    public static void registerScreens() {
        Loader.registerScreen(MenuTypesInit.TOWN_QUESTS.get(), (menu, inv, title) -> QuestsScreen.forTown(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.VILLAGER_QUESTS.get(), (menu, inv, title) -> QuestsScreen.forVillager(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.TOWN_QUESTS_REMOVE.get(), (menu, inv, title) -> new QuestRemoveConfirmScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.TOWN_WORK.get(), (menu, inv, title) -> new WorkScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.CREATE_STOCK_REQUEST.get(), (menu, inv, title) -> new CreateStockRequestScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.GATHERER_INVENTORY.get(), (menu, inv, title) -> new InventoryAndStatusScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.MULTI_VILLAGER.get(), (menu, inv, title) -> new MultiStatusScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.VILLAGER_STATS.get(), (menu, inv, title) -> new VillagerStatsScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.VILLAGER_ECONOMICS.get(), (menu, inv, title) -> new VillagerEconomicsScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.TOWN_ECONOMICS.get(), (menu, inv, title) -> new TownEconomicsScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.CONFIRM_JOB_UPGRADE.get(), (menu, inv, title) -> new JobUnlockConfirmScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.CONFIRM_JOB_CHANGE.get(), (menu, inv, title) -> new JobChangeConfirmScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.VILLAGER_BLOCKS_OF_PROGRESS.get(), (menu, inv, title) -> new VillagerBlockofProgressScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.TOWN_BLOCKS_OF_PROGRESS.get(), (menu, inv, title) -> new TownBlockofProgressScreen(menu, inv, title));
        Loader.registerScreen(MenuTypesInit.FLAG_CRAFTING.get(), (menu, inv, title) -> new ca.bradj.questown.gui.FlagCraftingScreen(menu, inv, title));
    }
}
