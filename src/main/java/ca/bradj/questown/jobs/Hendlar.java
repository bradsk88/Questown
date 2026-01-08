package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface Hendlar {
    <TOWN, POS, LEVEL> @Nullable TOWN hendle(HendlarInpoots<TOWN, POS, LEVEL> inputs);
}
