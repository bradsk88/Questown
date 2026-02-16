package ca.bradj.questown.commands.test;

import java.util.Collection;

public record TestExpectation(
        Collection<ExpectedProduct> products,
        int minCyclesExpected,
        int maxCyclesExpected
) {
    public record ExpectedProduct(
            String itemRegistryName,
            int minQuantity,
            int maxQuantity  // -1 means no upper bound
    ) {}
}
