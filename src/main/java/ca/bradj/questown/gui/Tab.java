package ca.bradj.questown.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import org.apache.logging.log4j.util.TriConsumer;

public record Tab(
        TriConsumer<PoseStack, Integer, Integer> renderFunc,
        Runnable onClick,
        String titleKey,
        boolean selected
) {
}