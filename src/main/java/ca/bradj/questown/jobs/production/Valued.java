package ca.bradj.questown.jobs.production;

public interface Valued<STATUS> {
    int value();

    STATUS minusValue(int i);
}
