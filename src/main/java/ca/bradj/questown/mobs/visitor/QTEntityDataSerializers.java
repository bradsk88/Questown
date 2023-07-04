package ca.bradj.questown.mobs.visitor;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class QTEntityDataSerializers {
    public static final EntityDataSerializer<List<ItemStack>> ITEM_STACK_LIST = new EntityDataSerializer<>() {
        @Override
        public void write(
                FriendlyByteBuf buf,
                List<ItemStack> stacks
        ) {
            buf.writeInt(stacks.size());
            for (ItemStack i : stacks) {
                buf.writeItem(i);
            }
        }

        @Override
        public List<ItemStack> read(FriendlyByteBuf buf) {
            int size = buf.readInt();
            ImmutableList.Builder<ItemStack> b = ImmutableList.builder();
            for (int i = 0; i < size; i++) {
                b.add(buf.readItem());
            }
            return b.build();
        }

        @Override
        public List<ItemStack> copy(List<ItemStack> p_135023_) {
            return ImmutableList.copyOf(p_135023_);
        }
    };
}
