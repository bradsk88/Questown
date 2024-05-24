package ca.bradj.questown.jobs;

public record AmountHeld(int value, boolean amountDoesNotMatter) {
    public static AmountHeld none() {
        return new AmountHeld(0, false);
    }

    public static AmountHeld ignored() {
        return new AmountHeld(0, true);
    }

    public boolean canHoldMore(int targetQuantity) {
        if (amountDoesNotMatter) {
            return true;
        }
        return value < targetQuantity;
    }


    public AmountHeld up() {
        return new AmountHeld(value + 1, amountDoesNotMatter);
    }
}
