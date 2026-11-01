package ca.bradj.questown.mobs.helperchicken;

import org.jetbrains.annotations.Nullable;

/**
 * Per-(beat, phase) row authored in {@link ChickenArcPresentation}'s table.
 *
 * <p>{@code hintKey} and {@code plainKey} may be {@code null} for terminal
 * states ({@link ChickenBeatState#COMPLETE}, {@link ChickenBeatState#FORFEIT})
 * which suppress the right-click monologue. For all other beats both keys
 * must be non-null.
 */
public record Presentation(
        ChickenArcBubbles.Bubble bubble,
        @Nullable String hintKey,
        @Nullable String plainKey
) {
}
