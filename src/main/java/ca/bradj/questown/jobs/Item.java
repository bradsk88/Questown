package ca.bradj.questown.jobs;

import java.util.Map;

public interface Item<I extends Item<I>> {
    boolean isEmpty();

    boolean isFood();

    I shrink();

    String getShortName();

    Map<String, String> getTags();
}
