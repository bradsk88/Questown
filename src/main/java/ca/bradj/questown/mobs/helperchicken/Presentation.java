package ca.bradj.questown.mobs.helperchicken;

import org.jetbrains.annotations.Nullable;

/** One lesson step's icon and hints. The hints are null only once the lessons are over. */
public record Presentation(
        ChickenArcBubbles.Bubble bubble,
        @Nullable String hintKey,
        @Nullable String plainKey
) {
}
