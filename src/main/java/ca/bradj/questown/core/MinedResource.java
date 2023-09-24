package ca.bradj.questown.core;

public class MinedResource implements Resource {
    private final String name;
    private final Rarity rarity;

    public MinedResource(
            String name,
            Rarity rarity
    ) {
        this.name = name;
        this.rarity = rarity;
    }

    @Override
    public int calculateValue() {
        return rarity.value;
    }
}
