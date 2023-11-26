package ca.bradj.questown.core;

public enum Rarity {
    ITS_EVERYWHERE(10),
    EASY_TO_FIND(50),
    SOMETIMES_HARD_TO_FIND(100),
    RARE(200);
    public final int value;

    Rarity(int i) {
        this.value = i;
    }
}
