package ca.bradj.questown.core;

public class CombatResource implements Resource {
    private final String name;
    private final Rarity rarity;

    public CombatResource(
            String name,
            Rarity rarity
    ) {
        this.name = name;
        this.rarity = rarity;
    }

    @Override
    public int calculateValue() {
        return rarity.value * 4;
    }
}
