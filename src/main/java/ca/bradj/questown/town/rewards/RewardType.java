package ca.bradj.questown.town.rewards;

import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.questown.town.quests.Reward;
import net.minecraftforge.registries.ForgeRegistryEntry;

public class RewardType<T extends Reward> extends ForgeRegistryEntry<RewardType<? extends Reward>> {

    private final Factory<T> factory;

    public RewardType(Factory<T> factory) {
        this.factory = factory;
    }

    public T create(RewardType<? extends Reward> rType, TownFlagBlockEntity entity) {
        return factory.newReward(rType, entity);
    }

    public interface Factory<PT extends Reward> {
        PT newReward(RewardType<? extends Reward> rType, TownFlagBlockEntity flag);
    }

    public static class Builder<BT extends Reward> {

        private final Factory<BT> provider;

        public Builder(Factory<BT> o) {
            this.provider = o;
        }

        public static <BT extends Reward> Builder<BT> of(
                Factory<BT> o
        ) {
            return new Builder<>(o);
        }

        public RewardType<BT> build(String string) {
            return new RewardType<>(this.provider);
        }
    }

}
