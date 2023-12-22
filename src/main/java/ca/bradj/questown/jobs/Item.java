package ca.bradj.questown.jobs;

public interface Item<I extends Item<I>> {
    boolean isEmpty();

    boolean isFood();

    I shrink();

    String getShortName();

     I unit();
}
