package ca.bradj.questown.town.activerecipes;

import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.serialization.RoomSerializer;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// MCActiveRecipes is a simple wrapper for ActiveRecipes that is coupled to Minecraft
public class MCActiveRecipes extends ActiveRecipes<ResourceLocation> {
    public static final Serializer SERIALIZER = new Serializer();

    private static final String NBT_NUM_ACTIVE_RECIPES = "num_active_recipes";
    private static final String NBT_ACTIVE_RECIPES = "active_recipes";

    private static final String NBT_RECIPE_ROOM = "recipe_room";
    private static final String NBT_RECIPE_ID = "recipe_id";

    public static class Serializer {

        public CompoundTag serializeNBT(MCActiveRecipes recipes) {
            CompoundTag c = new CompoundTag();
            c.putInt(NBT_NUM_ACTIVE_RECIPES, recipes.size());
            ListTag aq = new ListTag();
            for (Map.Entry<Room, ResourceLocation> e : recipes.activeRecipes.entrySet()) {
                CompoundTag rc = new CompoundTag();
                rc.put(NBT_RECIPE_ROOM, RoomSerializer.INSTANCE.serializeNBT(e.getKey()));
                rc.putString(NBT_RECIPE_ID, e.getValue().toString());
                aq.add(rc);
            }
            c.put(NBT_ACTIVE_RECIPES, aq);
            return c;
        }

        public void deserializeNBT(
                CompoundTag nbt,
                MCActiveRecipes recipes
        ) {
            ImmutableMap.Builder<Room, ResourceLocation> aqs = ImmutableMap.builder();
            int num = nbt.getInt(NBT_NUM_ACTIVE_RECIPES);
            ListTag aq = nbt.getList(NBT_ACTIVE_RECIPES, Tag.TAG_COMPOUND);
            for (int i = 0; i < num; i++) {
                CompoundTag compound = aq.getCompound(i);
                Room room = RoomSerializer.INSTANCE.deserializeNBT(compound.getCompound(NBT_RECIPE_ROOM));
                aqs.put(room, new ResourceLocation(compound.getString(NBT_RECIPE_ID)));
            }
            recipes.initialize(aqs.build().entrySet());
        }
    }
}
