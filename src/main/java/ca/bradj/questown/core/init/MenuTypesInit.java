package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.mc.Compat;
import net.minecraft.world.inventory.MenuType;

public class MenuTypesInit {

    // NOTE: You can't have more than one menu per container type.
    public static final Holder<MenuType<TownQuestsContainer>> TOWN_QUESTS =
        Loader.registerMenu("town_quests", TownQuestsContainer::ForClient);
    public static final Holder<MenuType<VillagerQuestsContainer>> VILLAGER_QUESTS =
        Loader.registerMenu("villager_quests", VillagerQuestsContainer::ForClient);
    public static final Holder<MenuType<TownRemoveQuestsContainer>> TOWN_QUESTS_REMOVE =
        Loader.registerMenu("town_quests_remove", TownRemoveQuestsContainer::read);
    public static final Holder<MenuType<TownWorkContainer>> TOWN_WORK =
        Loader.registerMenu("town_work", TownWorkContainer::ForClientSide);
    public static final Holder<MenuType<VisitorQuestsContainer>> VISITOR_QUESTS =
        Loader.registerMenu("visitor_quests", VisitorQuestsContainer::new);
    public static final Holder<MenuType<InventoryAndStatusMenu>> GATHERER_INVENTORY =
        Loader.registerMenu("gatherer_inventory", InventoryAndStatusMenu::ForClientSide);
    public static final Holder<MenuType<MultiStatusMenu>> MULTI_VILLAGER =
        Loader.registerMenu("multi_villager_status", MultiStatusMenu::ForClientSide);
    public static final Holder<MenuType<VillagerStatsMenu>> VILLAGER_STATS =
        Loader.registerMenu("villager_stats", VillagerStatsMenu::ForClientSide);
    public static final Holder<MenuType<VillagerEconomicsMenu>> VILLAGER_ECONOMICS =
        Loader.registerMenu("villager_economics", VillagerEconomicsMenu::ForClientSide);
    public static final Holder<MenuType<VillagerBlockofProgressMenu>> VILLAGER_BLOCKS_OF_PROGRESS =
        Loader.registerMenu("blocks_of_progress", VillagerBlockofProgressMenu::ForClientSide);
    public static final Holder<MenuType<TownEconomicsMenu>> TOWN_ECONOMICS =
        Loader.registerMenu("town_economics", TownEconomicsMenu::ForClientSide);
    public static final Holder<MenuType<CreateStockRequestContainer>> CREATE_STOCK_REQUEST =
        Loader.registerMenu("create_stock_request", CreateStockRequestContainer::ForClientSide);
    public static final Holder<MenuType<JobUnlockConfirmMenu>> CONFIRM_JOB_UPGRADE =
        Loader.registerMenu("confirm_job_upgrade", JobUnlockConfirmMenu::ForClientSide);
    public static final Holder<MenuType<JobChangeConfirmMenu>> CONFIRM_JOB_CHANGE =
        Loader.registerMenu("confirm_job_change", JobChangeConfirmMenu::ForClientSide);
    public static final Holder<MenuType<TownBlockofProgressMenu>> TOWN_BLOCKS_OF_PROGRESS =
        Loader.registerMenu("town_blocks_of_progress", TownBlockofProgressMenu::ForClientSide);
    public static final Holder<MenuType<FlagCraftingMenu>> FLAG_CRAFTING =
        Loader.registerMenu("flag_crafting", FlagCraftingMenu::ForClientSide);

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }



    public static void boot() {}
}
