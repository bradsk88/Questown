package ca.bradj.questown.core;

public class FarmedResource implements Resource {
    private final String name;
    private final Rarity rarity;

    public FarmedResource(
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
