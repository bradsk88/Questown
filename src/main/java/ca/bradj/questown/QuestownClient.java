package ca.bradj.questown;

import ca.bradj.questown.blocks.entity.renderer.AxeRackBlockEntityRenderer;
import ca.bradj.questown.blocks.entity.renderer.FoodDisplayEntityRenderer;
import ca.bradj.questown.blocks.entity.renderer.HospitalBedEntityRenderer;
import ca.bradj.questown.blocks.entity.renderer.PlateBlockEntityRenderer;
import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.EntitiesInit;
import ca.bradj.questown.core.init.MenuTypesInit;
import ca.bradj.questown.core.init.TilesInit;
import ca.bradj.questown.gui.CreateStockRequestScreen;
import ca.bradj.questown.gui.InventoryAndStatusScreen;
import ca.bradj.questown.gui.JobChangeConfirmScreen;
import ca.bradj.questown.gui.JobUnlockConfirmScreen;
import ca.bradj.questown.gui.QuestRemoveConfirmScreen;
import ca.bradj.questown.gui.QuestsScreen;
import ca.bradj.questown.gui.TownBlockofProgressScreen;
import ca.bradj.questown.gui.TownEconomicsScreen;
import ca.bradj.questown.gui.VillagerBlockofProgressScreen;
import ca.bradj.questown.gui.VillagerEconomicsScreen;
import ca.bradj.questown.gui.VillagerStatsScreen;
import ca.bradj.questown.gui.WorkScreen;
import ca.bradj.questown.gui.town.status.MultiStatusScreen;
import ca.bradj.questown.loader.Loader;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.helperchicken.HelperChickenRenderer;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Client-only bootstrap: renderers and the menu-screen captures. Loaded only from the
 * {@code questown.client} mod entry (dist=CLIENT), so a dedicated server never loads this
 * class (or the client classes it references) and NeoForge's runtime dist cleaner stays
 * quiet. The common {@link Questown} keeps only loader-neutral code.
 */
public final class QuestownClient {

    private QuestownClient() {
    }

    public static void onClientSetup() {
        Compat.setCutoutRenderType(BlocksInit.FALSE_DOOR_BLOCK.get());
        Compat.setCutoutRenderType(BlocksInit.FALSE_WALL_BLOCK.get());

        EntityRenderers.register(EntitiesInit.VISITOR.get(), VisitorMobRenderer::new);
        EntityRenderers.register(EntitiesInit.HELPER_CHICKEN.get(), HelperChickenRenderer::new);

        BlockEntityRenderers.register(TilesInit.PLATE.get(), PlateBlockEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.FOOD_DISPLAY.get(), FoodDisplayEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.AXE_RACK.get(), AxeRackBlockEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.HOSPITAL_BED.get(), HospitalBedEntityRenderer::new);
        BlockEntityRenderers.register(TilesInit.TOWN_FLAG.get(), ca.bradj.questown.blocks.entity.renderer.TownFlagDeedRenderer::new);
    }

    /**
     * Menu-screen captures, invoked at client init (before client setup). NeoForge fires its
     * menu-screen registration event at client init, before any mod-bus setup event, so the
     * captures must exist before then; the client island replays them
     * (see {@code loader.neo.NeoLoaderClient}).
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
