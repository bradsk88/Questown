package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.TownQuestsContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ContainerTypesInit {


    public static final DeferredRegister<MenuType<?>> TYPES = DeferredRegister.create(
            ForgeRegistries.CONTAINERS, Questown.MODID
    );
    public static RegistryObject<MenuType<TownQuestsContainer>> TOWN_QUESTS;

    public static void register() {
        TOWN_QUESTS = TYPES.register(
                "town_quests", () -> IForgeMenuType.create(TownQuestsContainer::new)
        );
    }

}