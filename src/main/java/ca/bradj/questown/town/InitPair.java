package ca.bradj.questown.town;

import net.minecraft.nbt.CompoundTag;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public record InitPair(
        BiFunction<CompoundTag, TownFlagBlockEntity, Boolean> fromTag,
        Consumer<TownFlagBlockEntity> onFlagPlace
) {
}
