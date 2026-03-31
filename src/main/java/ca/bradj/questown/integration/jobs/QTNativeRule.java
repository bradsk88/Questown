package ca.bradj.questown.integration.jobs;

/**
 * Marker interface declaring that a {@link JobPhaseModifier} rule is
 * <strong>Tier 1 (QT-native)</strong> — it uses only {@code QTWorldAccess}
 * methods and never calls {@link ca.bradj.questown.world.QTWorldAccess#asServerLevel()}.
 *
 * <h3>Tier system</h3>
 * <ul>
 *   <li><b>Tier 1 (QT-native)</b>: rule accesses the world only through
 *       {@code QTWorldAccess} methods. It works correctly in all contexts:
 *       realtime, time warp, and unit tests (using {@code TestWorldAccess}).
 *       Declare by implementing this interface.</li>
 *   <li><b>Tier 2 (MC-native)</b>: rule calls
 *       {@code event.world().asServerLevel()} (or accesses Minecraft APIs
 *       directly). It may degrade or be skipped during time warp.
 *       Do <em>not</em> implement this interface.</li>
 * </ul>
 *
 * <h3>For modders</h3>
 * <p>When writing a custom {@code JobPhaseModifier}, implement
 * {@code QTNativeRule} only if your rule accesses the world exclusively
 * through the {@code QTWorldAccess} parameter on the event (e.g.
 * {@code event.world().getBlockIntProperty(...)},
 * {@code event.world().insertIntoContainer(...)}). If your rule calls
 * {@code asServerLevel()} to reach Minecraft APIs, it is Tier 2 — leave
 * out this interface and ensure it gracefully handles a null return from
 * {@code asServerLevel()} (which occurs during time warp and in tests).</p>
 *
 * <p>At server startup, Questown logs a warning for each registered
 * Tier 2 rule so you can track migration progress.</p>
 *
 * @see ca.bradj.questown.integration.SpecialRulesRegistry
 * @see ca.bradj.questown.world.QTWorldAccess
 */
public interface QTNativeRule {
}
