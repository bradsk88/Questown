package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.Item;

import java.util.Collection;

public class ValidatedInventoryHandle<X extends Item<X>> implements InventoryHandle<X> {

    InventoryHandle<X> delegate;

    public ValidatedInventoryHandle(
            InventoryHandle<X> delegate,
            int inventorySize
    ) throws ItemCountMismatch {
        this(delegate, inventorySize, false);
    }

    /**
     * @deprecated Not supported
     */
    public static <X extends Item<X>> ValidatedInventoryHandle<X> unvalidated(
            InventoryHandle<X> delegate
    ) {
        try {
            return new ValidatedInventoryHandle<>(delegate, 0, true);
        } catch (ItemCountMismatch e) {
            throw new RuntimeException(e);
        }
    }

    private ValidatedInventoryHandle(
            InventoryHandle<X> delegate,
            int inventorySize,
            boolean skipValidation
    ) throws ItemCountMismatch {
        this.delegate = delegate;
        if (skipValidation) {
            return;
        }
        int found = 0;
        for (X x : delegate.getItems()) {
            if (x == null) {
                throw new ItemCountMismatch(inventorySize);
            }
            found++;
            if (found > inventorySize) {
                throw new ItemCountMismatch(inventorySize);
            }
        }
        if (found < inventorySize) {
            throw new ItemCountMismatch(inventorySize);
        }
    }

    @Override
    public Collection<X> getItems() {
        return delegate.getItems();
    }

    @Override
    public void set(
            int ii,
            X shrink
    ) {
        delegate.set(ii, shrink);
    }
}
