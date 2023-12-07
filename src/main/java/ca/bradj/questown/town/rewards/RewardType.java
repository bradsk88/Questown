package ca.bradj.questown.town.rewards;

import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.quests.MCReward;
import net.minecraft.resources.ResourceLocation;

public class RewardType<T extends MCReward> {

    private final Factory<T> factory;
    private final ResourceLocation id;

    public RewardType(Factory<T> factory,
                      ResourceLocation id
    ) {
        this.factory = factory;
        this.id = id;
    }

    public T create(RewardType<? extends MCReward> rType, TownInterface entity) {
        return factory.newReward(rType, entity);
    }

    public ResourceLocation getRegistryName() {
        return id;
    }

    public interface Factory<PT extends MCReward> {
        PT newReward(RewardType<? extends MCReward> rType, TownInterface flag);
    }

    public static class Builder<BT extends MCReward> {

        private final Factory<BT> provider;

        public Builder(Factory<BT> o) {
            this.provider = o;
        }

        public static <BT extends MCReward> Builder<BT> of(
                Factory<BT> o
        ) {
            return new Builder<>(o);
        }

        public RewardType<BT> build(ResourceLocation id) {
            return new RewardType<>(this.provider, id);
        }
    }

}
