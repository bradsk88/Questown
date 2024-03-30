package ca.bradj.questown.gui;

import org.apache.logging.log4j.util.TriConsumer;

public record Tab(
        TriConsumer<RenderContext, Integer, Integer> renderFunc,
        Runnable onClick,
        String titleKey,
        boolean selected
) {
}