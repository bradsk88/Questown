package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.TownQuestsContainer;
import ca.bradj.questown.gui.VisitorQuestsContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class MenuTypesInit {


    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            ForgeRegistries.CONTAINERS, Questown.MODID
    );
    public static RegistryObject<MenuType<TownQuestsContainer>> TOWN_QUESTS = MENUS.register(
            "town_quests", () -> IForgeMenuType.create(TownQuestsContainer::new)
    );
    public static RegistryObject<MenuType<VisitorQuestsContainer>> VISITOR_QUESTS = MENUS.register(
            "visitor_quests", () -> IForgeMenuType.create(VisitorQuestsContainer::new)
    );

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

}