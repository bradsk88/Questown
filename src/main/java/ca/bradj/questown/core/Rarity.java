package ca.bradj.questown.core;

public enum Rarity {
    EVERYWHERE(10),
    COMMON(50),
    MEDIUM(100),
    RARE(200);
    public final int value;

    Rarity(int i) {
        this.value = i;
    }
}
