package ca.bradj.questown;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.loader.Loader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.Logger;

/** The mod's shared setup: its id, logging, and the setup steps each loader calls. */
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

    public static ResourceLocation ResourceLocation(
            String id,
            Object... args
    ) {
        return new ResourceLocation(Questown.MODID, String.format(id, args));
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(QuestownNetwork::init);
        ca.bradj.questown.jobs.declarative.DeclarativeJobs.staticInitialize();
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        Compat.setCutoutRenderType(BlocksInit.FALSE_DOOR_BLOCK.get());
        Compat.setCutoutRenderType(BlocksInit.FALSE_WALL_BLOCK.get());
        MenuScreens.<TownQuestsContainer, QuestsScreen<TownQuestsContainer>>register(
                MenuTypesInit.TOWN_QUESTS.get(),
                QuestsScreen::forTown
        );
        MenuScreens.<VillagerQuestsContainer, QuestsScreen<VillagerQuestsContainer>>register(
                MenuTypesInit.VILLAGER_QUESTS.get(),
                QuestsScreen::forVillager
        );
        MenuScreens.register(MenuTypesInit.TOWN_QUESTS_REMOVE.get(), QuestRemoveConfirmScreen::new);
        MenuScreens.register(MenuTypesInit.TOWN_WORK.get(), WorkScreen::new);
        MenuScreens.register(MenuTypesInit.CREATE_STOCK_REQUEST.get(), CreateStockRequestScreen::new);
        MenuScreens.register(MenuTypesInit.GATHERER_INVENTORY.get(), InventoryAndStatusScreen::new);
        MenuScreens.register(MenuTypesInit.MULTI_VILLAGER.get(), MultiStatusScreen::new);
        MenuScreens.register(MenuTypesInit.VILLAGER_STATS.get(), VillagerStatsScreen::new);
        MenuScreens.register(MenuTypesInit.VILLAGER_ECONOMICS.get(), VillagerEconomicsScreen::new);
        MenuScreens.register(MenuTypesInit.TOWN_ECONOMICS.get(), TownEconomicsScreen::new);
        MenuScreens.register(MenuTypesInit.CONFIRM_JOB_UPGRADE.get(), JobUnlockConfirmScreen::new);
        MenuScreens.register(MenuTypesInit.CONFIRM_JOB_CHANGE.get(), JobChangeConfirmScreen::new);
        MenuScreens.register(MenuTypesInit.VILLAGER_BLOCKS_OF_PROGRESS.get(), VillagerBlockofProgressScreen::new);
        MenuScreens.register(MenuTypesInit.TOWN_BLOCKS_OF_PROGRESS.get(), TownBlockofProgressScreen::new);
        MenuScreens.register(MenuTypesInit.FLAG_CRAFTING.get(), FlagCraftingScreen::new);
        event.enqueueWork(() -> EntityRenderers.register(
                EntitiesInit.VISITOR.get(),
                VisitorMobRenderer::new
        ));
        event.enqueueWork(() -> BlockEntityRenderers.register(
                TilesInit.PLATE.get(),
                PlateBlockEntityRenderer::new
        ));
        event.enqueueWork(() -> BlockEntityRenderers.register(
                TilesInit.FOOD_DISPLAY.get(),
                FoodDisplayEntityRenderer::new
        ));
        event.enqueueWork(() -> BlockEntityRenderers.register(
                TilesInit.AXE_RACK.get(),
                AxeRackBlockEntityRenderer::new
        ));
        event.enqueueWork(() -> BlockEntityRenderers.register(
                TilesInit.HOSPITAL_BED.get(),
                HospitalBedEntityRenderer::new
        ));
        event.enqueueWork(() -> EntityRenderers.register(
                ca.bradj.questown._vanilla.entities.EntitiesInit.FISHIN_HOOK.get(),
                FishingHookRenderer::new
        ));
    }
}
