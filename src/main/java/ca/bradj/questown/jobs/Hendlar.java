package ca.bradj.questown.jobs;

import org.jetbrains.annotations.Nullable;

public interface Hendlar {
    <TOWN> @Nullable TOWN hendle(HendlarInpoots<TOWN> inputs);
}
