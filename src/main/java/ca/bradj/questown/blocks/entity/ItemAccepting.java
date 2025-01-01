package ca.bradj.questown.blocks.entity;

public interface ItemAccepting<I> {
    boolean setItem(
            int index,
            I item
    );
}
