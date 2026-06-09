package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.List;

public record TestExpectation(
        Collection<ExpectedProduct> products,
        int minCyclesExpected,
        int maxCyclesExpected,
        Collection<ExpectedContainerContent> containerContents
) {
    // Compatibility constructor: the town-wide product-delta model (used by all existing
    // scenarios), with no per-position container assertions.
    public TestExpectation(
            Collection<ExpectedProduct> products,
            int minCyclesExpected,
            int maxCyclesExpected
    ) {
        this(products, minCyclesExpected, maxCyclesExpected, List.of());
    }

    public TestExpectation withContainerContents(Collection<ExpectedContainerContent> contents) {
        return new TestExpectation(products, minCyclesExpected, maxCyclesExpected, contents);
    }

    public record ExpectedProduct(
            String itemRegistryName,
            Integer minDelta,
            Integer maxDelta
    ) {}

    /**
     * Per-{@link BlockPos} chest delta assertion: the count of {@code item} in the chest at
     * {@code chest} must change by a value within {@code [minDelta, maxDelta]} (either bound
     * {@code null} means unbounded on that side).
     *
     * <p>A relocation asserts the <em>source</em> chest with a negative delta AND the
     * <em>target</em> chest with a positive delta. The town-wide {@link ExpectedProduct} model
     * cannot see a relocation (its town-wide delta is ~0), so this per-position oracle is the
     * only way to gate a fetch — and it doubles as a dupe/loss guard for warp parity.
     */
    public record ExpectedContainerContent(
            BlockPos chest,
            String item,
            Integer minDelta,
            Integer maxDelta
    ) {}
}
