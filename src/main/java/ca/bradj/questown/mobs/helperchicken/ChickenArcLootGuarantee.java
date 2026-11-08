package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Adds Worldly Seeds to a gather's results during the helper chicken's lessons, once per town. */
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
