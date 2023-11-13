package ca.bradj.questown.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;

public class UIWork {

    private final Ingredient resultWanted;

    public UIWork(Ingredient resultWanted) {
        this.resultWanted = resultWanted;
    }

    public Ingredient getResultWanted() {
        return resultWanted;
    }

    public static class Serializer {
        public UIWork fromNetwork(
                FriendlyByteBuf buf
        ) {
            return new UIWork(Ingredient.fromNetwork(buf));
        }

        public void toNetwork(
                FriendlyByteBuf buf,
                Ingredient q
        ) {
            q.toNetwork(buf);
        }
    }
}
