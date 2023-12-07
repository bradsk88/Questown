package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.HeldItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class MCCoupledHeldItem implements HeldItem<MCCoupledHeldItem, MCTownItem> {

    private final Consumer<MCHeldItem> onShrink;
    private MCHeldItem delegate;

    private MCCoupledHeldItem(
            MCHeldItem delegate,
            Consumer<MCHeldItem> onShrink
    ) {
        this.delegate = delegate;
        this.onShrink = onShrink;
    }

    public static MCCoupledHeldItem fromMCItemStack(ItemStack item, Consumer<MCHeldItem> onShrink) {
        return new MCCoupledHeldItem(MCHeldItem.fromMCItemStack(item), onShrink);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean isFood() {
        return delegate.isFood();
    }

    @Override
    public MCCoupledHeldItem shrink() {
        delegate = delegate.shrink();
        onShrink.accept(delegate);
        return this;
    }

    @Override
    public String getShortName() {
        return delegate.getShortName();
    }

    @Override
    public boolean isLocked() {
        return delegate.isLocked();
    }

    @Override
    public MCTownItem get() {
        return delegate.get();
    }

    @Override
    public MCCoupledHeldItem locked() {
        delegate = delegate.locked();
        return this;
    }

    @Override
    public MCCoupledHeldItem unlocked() {
        delegate = delegate.unlocked();
        return this;
    }

    @Override
    public @Nullable String acquiredViaLootTablePrefix() {
        return delegate.acquiredViaLootTablePrefix();
    }

    @Override
    public @Nullable String foundInBiome() {
        return delegate.foundInBiome();
    }

    @Override
    public String toShortString() {
        return delegate.toShortString();
    }
}
