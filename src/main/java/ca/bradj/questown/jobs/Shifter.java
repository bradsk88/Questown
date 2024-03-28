package ca.bradj.questown.jobs;

public interface Shifter<POS> {

    POS north(POS ref);

    POS south(POS ref);

    POS east(POS ref);

    POS west(POS ref);
}
