package ca.bradj.questown.core;

public record Coordinate(Integer x, Integer y) {
    public Coordinate shifted(
            int i,
            int i1
    ) {
        return new Coordinate(x + i, y + i1);
    }
}
