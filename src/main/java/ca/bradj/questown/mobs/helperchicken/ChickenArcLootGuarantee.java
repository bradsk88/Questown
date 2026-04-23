package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepends a Worldly Seeds drop to a gatherer-style result set during the
 * helper-chicken onboarding arc (U7, R15).
 *
 * <p>Idempotent by design: the guard reads
 * {@code chicken-first-gather-worldly-seeds-fired} on the flag BE, which U4
 * flips at its rising edge once seeds land in any town container. The two
 * wrap sites ({@code RealtimeWorldInteraction.getResults} and
 * {@code TimeWarpWorldInteraction.getResults}) therefore share this helper
 * without double-counting — the bit guarantees exactly-one-prepend per town.
 *
 * <p>The prepend fires regardless of which job invoked the result generator.
 * In practice v1 starts with only the gatherer job available, so a non-gatherer
 * catch is a theoretical edge case the bit's once-only semantics still bound.
 */
public final class ChickenArcLootGuarantee {

    private ChickenArcLootGuarantee() {
    }

    public static Iterable<MCHeldItem> maybePrepend(
            TownFlagBlockEntity flag,
            Iterable<MCHeldItem> base
    ) {
        if (!shouldPrepend(flag)) {
            return base;
        }
        MCHeldItem seeds = MCHeldItem.fromMCItemStack(
                new ItemStack(ItemsInit.WORLDLY_SEEDS.get())
        );
        List<MCHeldItem> out = new ArrayList<>();
        out.add(seeds);
        base.forEach(out::add);
        return out;
    }

    public static boolean shouldPrepend(TownFlagBlockEntity flag) {
        if (flag == null) {
            return false;
        }
        if (!flag.getChickenEverSpawned()) {
            return false;
        }
        if (flag.getChickenArcForfeit()) {
            return false;
        }
        return !flag.getChickenFirstGatherWorldlySeedsFired();
    }
}
