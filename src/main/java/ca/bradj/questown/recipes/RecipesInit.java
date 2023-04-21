package ca.bradj.questown.recipes;

import ca.bradj.questown.Questown;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class RecipesInit {

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS, Questown.MODID
    );

    public static final RegistryObject<RoomRecipe.Serializer> ROOM_SERIALIZER = SERIALIZERS.register(
            "room", RoomRecipe.Serializer::new
    );
    public static RecipeType<RoomRecipe> ROOM = new RoomRecipe.Type();

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

}
