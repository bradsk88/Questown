package ca.bradj.questown.jobs.production;

import ca.bradj.questown.gui.SessionUniqueOrdinals;
import ca.bradj.questown.jobs.IStatusFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;

public class ProductionStatus implements IProductionStatus<ProductionStatus> {

    private final int value;

    // Numbers 0-9 are reserved for job-specific statuses.
    // TODO: Should probably build something more flexible
    private static final int firstNonCustomIndex = 10;
    private static int nextIndex = firstNonCustomIndex;
    private static final HashSet<ProductionStatus> allStatuses = new HashSet<>();

    static {
        for (int i = 0; i < firstNonCustomIndex; i++) {
            SessionUniqueOrdinals.register(ProductionStatus.fromJobBlockStatus(i));
        }
    }

    private static ProductionStatus register(ProductionStatus ps) {
        allStatuses.add(ps);
        return SessionUniqueOrdinals.register(ps);
    }

    public static ImmutableSet<ProductionStatus> allStatuses() {
        return ImmutableSet.copyOf(allStatuses);
    }

    public static final ProductionStatus DROPPING_LOOT = register(
            new ProductionStatus("DROPPING_LOOT", nextIndex++)
    );
    public static final ProductionStatus NO_SPACE = register(
            new ProductionStatus("NO_SPACE", nextIndex++)
    );
    public static final ProductionStatus GOING_TO_JOB = register(
            new ProductionStatus("GOING_TO_JOB", nextIndex++)
    );
    public static final ProductionStatus NO_SUPPLIES = register(
            new ProductionStatus("NO_SUPPLIES", nextIndex++)
    );
    public static final ProductionStatus COLLECTING_SUPPLIES = register(
            new ProductionStatus("COLLECTING_SUPPLIES", nextIndex++)
    );
    public static final ProductionStatus IDLE = register(
            new ProductionStatus("IDLE", nextIndex++)
    );
    public static final ProductionStatus EXTRACTING_PRODUCT = register(
            new ProductionStatus("EXTRACTING_PRODUCT", nextIndex++)
    );
    public static final ProductionStatus RELAXING = register(
            new ProductionStatus("RELAXING", nextIndex++)
    );
    public static final ProductionStatus WAITING_FOR_TIMED_STATE = register(
            new ProductionStatus("WAITING_FOR_TIMED_STATE", nextIndex++)
    );

    public static final IStatusFactory<ProductionStatus> FACTORY = new IStatusFactory<>() {
        @Override
        public ProductionStatus droppingLoot() {
            return DROPPING_LOOT;
        }

        @Override
        public ProductionStatus noSpace() {
            return NO_SPACE;
        }

        @Override
        public ProductionStatus goingToJobSite() {
            return GOING_TO_JOB;
        }

        @Override
        public ProductionStatus noSupplies() {
            return NO_SUPPLIES;
        }

        @Override
        public ProductionStatus collectingSupplies() {
            return COLLECTING_SUPPLIES;
        }

        @Override
        public ProductionStatus idle() {
            return IDLE;
        }

        @Override
        public ProductionStatus extractingProduct() {
            return EXTRACTING_PRODUCT;
        }

        @Override
        public ProductionStatus relaxing() {
            return RELAXING;
        }

        @Override
        public ProductionStatus waitingForTimedState() {
            return WAITING_FOR_TIMED_STATE;
        }
    };
    private final String name;

    public static ProductionStatus fromJobBlockStatus(int s) {
        if (s >= firstNonCustomIndex) {
            throw new IllegalStateException("Not a valid job block status: " + s);
        }
        return new ProductionStatus("state:" + s, s);
    }

    private ProductionStatus(String name, int i) {
        this.value = i;
        this.name = name;
    }

    public static ProductionStatus from(String s) {
        int i = Integer.parseInt(s);
        return new ProductionStatus(s, i);
    }

    @Override
    public IStatusFactory<ProductionStatus> getFactory() {
        return FACTORY;
    }

    @Override
    public boolean isGoingToJobsite() {
        return GOING_TO_JOB.equals(this);
    }

    @Override
    public boolean isWorkingOnProduction() {
        return isExtractingProduct() || this.value < firstNonCustomIndex;
    }

    @Override
    public boolean isExtractingProduct() {
        return EXTRACTING_PRODUCT.equals(this);
    }

    @Override
    public boolean isDroppingLoot() {
        return DROPPING_LOOT.equals(this);
    }

    @Override
    public boolean isCollectingSupplies() {
        return COLLECTING_SUPPLIES.equals(this);
    }

    @Override
    public String name() {
        return Integer.toString(value);
    }

    @Override
    public boolean isUnset() {
        return this.value < 0;
    }

    @Override
    public boolean isAllowedToTakeBreaks() {
        if (this.isWorkingOnProduction()) {
            return false;
        }
        return !ImmutableList.of(
                GOING_TO_JOB,
                DROPPING_LOOT,
                COLLECTING_SUPPLIES,
                EXTRACTING_PRODUCT
        ).contains(this);
    }

    @Override
    public boolean canWork() {
        return !isAllowedToTakeBreaks();
    }

    @Override
    public @Nullable String getCategoryId() {
        if (isWorkingOnProduction()) {
            return null; // Should use job ID
        }
        return "production";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductionStatus that = (ProductionStatus) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    public int getProductionState() {
        if (value >= firstNonCustomIndex) {
            throw new IllegalStateException("Invalid production status: " + value);
        }
        return value;
    }

    @Override
    public String toString() {
        return "ProductionStatus{" +
                "value=" + value +
                ", name='" + name + '\'' +
                '}';
    }
}
