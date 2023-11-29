package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.items.QTNBT;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class TownJobHandleSerializer {
    public static final TownJobHandleSerializer INSTANCE = new TownJobHandleSerializer();

    public Tag serializeNBT(WorkHandle workHandle) {
        CompoundTag tag = new CompoundTag();
        QTNBT.put(tag, "boards", serializeBoards(workHandle));
        QTNBT.put(tag, "requests", serializeRequests(workHandle));
        return tag;
    }

    public void deserializeNBT(
            CompoundTag compound,
            WorkHandle workHandle
    ) {
        deserializeBoards(QTNBT.getList(compound, "boards")).forEach(
                workHandle::registerJobBoard
        );
        workHandle.addWork(deserializeRequests(QTNBT.getList(compound, "requests")));
    }

    @NotNull
    private static ListTag serializeBoards(WorkHandle workHandle) {
        ListTag list = new ListTag();
        workHandle.jobBoards.forEach(v -> {
            CompoundTag bT = new CompoundTag();
            bT.putInt("x", v.getX());
            bT.putInt("y", v.getY());
            bT.putInt("z", v.getZ());
            list.add(bT);
        });
        return list;
    }

    private Iterable<BlockPos> deserializeBoards(ListTag boards) {
        return boards.stream().map(v -> {
            CompoundTag vc = (CompoundTag) v;
            return new BlockPos(vc.getInt("x"), vc.getInt("y"), vc.getInt("z"));
        }).toList();
    }

    private Tag serializeRequests(WorkHandle workHandle) {
        ListTag list = new ListTag();
        workHandle.requestedResults.forEach(v -> {
            CompoundTag tag = new CompoundTag();
            JsonObject asJsonObject = v.toJson().getAsJsonObject();
            if (asJsonObject.has("item")) {
                tag.putString("ingredient_item", asJsonObject.get("item").getAsString());
            }
            else if (asJsonObject.has("tag")) {
                tag.putString("ingredient_tag", asJsonObject.get("tag").getAsString());
            } else {
                QT.FLAG_LOGGER.error("Request had no tag. Request will be lost. [{}]", asJsonObject);
            }
            list.add(tag);
        });
        return list;
    }

    private Collection<Ingredient> deserializeRequests(ListTag requests) {
        ImmutableList.Builder<Ingredient> b = ImmutableList.builder();

        requests.forEach(v -> {
            CompoundTag vc = (CompoundTag) v;
            if (vc.contains("ingredient_item")) {
                String itemName = vc.getString("ingredient_item");
                @Nullable Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemName));
                b.add(Ingredient.of(item));
                return;
            }
            if (vc.contains("ingredient_tag")) {
                String tagName = vc.getString("ingredient_tag");
                @NotNull TagKey<Item> tk = ForgeRegistries.ITEMS.tags().createTagKey(new ResourceLocation(tagName));
                b.add(Ingredient.of(tk));
                return;
            }
            QT.FLAG_LOGGER.error("Request was stored without an ID. Request will be lost. [{}]", vc);
        });

        return b.build();
    }

}
