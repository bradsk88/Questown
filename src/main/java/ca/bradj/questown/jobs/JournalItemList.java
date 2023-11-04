package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class JournalItemList<H extends HeldItem<H, ?>> extends ArrayList<H> {

    public JournalItemList(
            int initialCapacity,
            EmptyFactory<H> ef
    ) {
        super(initialCapacity);
        for (int i = 0; i < initialCapacity; i++) {
            this.add(ef.makeEmptyItem());
        }
    }

    public void setItems(Iterable<H> items) {
        ImmutableList.Builder<H> b = ImmutableList.builder();
        items.forEach(b::add);
        ImmutableList<H> initItems = b.build();
        if (initItems.size() != size()) {
            throw new IllegalArgumentException(String.format(
                    "Argument to setItems is wrong length. Should be %d but got %s", size(), initItems.size()
            ));
        }
        clear();
        addAll(initItems);
    }
}
