package ca.bradj.questown.core;

import java.util.Collection;

public class CraftedResource implements Resource {
    private final String name;
    private final int outout;
    private final Collection<? extends Resource> ingredients;

    public CraftedResource(
            String name, // The name is not used for anything. It just makes the usage of this easier to understand.
            int output,
            Collection<? extends Resource> ingredients
    ) {
        this.name = name;
        this.outout = output;
        this.ingredients = ingredients;
    }

    @Override
    public int calculateValue() {
        int sum = ingredients.stream().mapToInt(Resource::calculateValue).sum();
        return (int) ((float) sum / (float) outout) + 10;
    }
}
