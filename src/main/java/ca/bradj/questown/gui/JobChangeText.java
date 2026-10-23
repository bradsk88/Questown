package ca.bradj.questown.gui;

import java.util.Optional;

/**
 * Which line the change-job panel shows (pure so it is unit-testable; the screen turns it into
 * components). It names the next action, inserting a Block of Progress or clicking Unlock, and
 * the real timing: the change lands the next morning in survival but at once in creative
 * ({@code JobRootChangeMessage} carries the player's creative flag). r9 findings #2.
 *
 * @param key     the lang key of the line
 * @param root    the job root the line names, if any (promised by an onboarding quest)
 * @param whenKey the lang key of the timing ("starting tomorrow morning" / "starting now")
 */
public record JobChangeText(String key, Optional<String> root, String whenKey) {

    /**
     * @param inserted     a Block of Progress is in the slot
     * @param creative     the viewing player is in creative
     * @param promisedRoot the root an open onboarding job-change quest guarantees
     * @param blessed      the inserted Block of Progress is a "special" one (the screen supplies its root)
     */
    public static JobChangeText of(
            boolean inserted,
            boolean creative,
            Optional<String> promisedRoot,
            boolean blessed
    ) {
        String when = creative ? "menu.unlock_root.when_now" : "menu.unlock_root.when_morning";
        if (promisedRoot.isPresent()) {
            String key = inserted ? "menu.unlock_root.click_unlock_promised" : "menu.unlock_root.insert_bop_promised";
            return new JobChangeText(key, promisedRoot, when);
        }
        if (blessed) {
            return new JobChangeText("menu.unlock_root.insert_bop_blessed", Optional.empty(), when);
        }
        String key = inserted ? "menu.unlock_root.click_unlock" : "menu.unlock_root.insert_bop";
        return new JobChangeText(key, Optional.empty(), when);
    }
}
