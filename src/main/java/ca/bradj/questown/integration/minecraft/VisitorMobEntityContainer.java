package ca.bradj.questown.integration.minecraft;

import ca.bradj.questown.jobs.GathererJournal;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class VisitorMobEntityContainer implements Container {

    private final GathererJournal<MCTownItem> journal;

    public VisitorMobEntityContainer(
            GathererJournal<MCTownItem> journal
    ) {
        this.journal = journal;
    }

    @Override
    public int getContainerSize() {
        return journal.getCapacity();
    }

    @Override
    public boolean isEmpty() {
        return !journal.hasAnyItems();
    }

    @Override
    public ItemStack getItem(int p_18941_) {
        return new ItemStack(journal.getItems().get(p_18941_).get(), 1);
    }

    @Override
    public ItemStack removeItem(
            int idx,
            int amount
    ) {
        if (amount > 1) {
            throw new IllegalArgumentException("Gatherers do not support stacking");
        }
        return new ItemStack(journal.removeItem(idx).get(), 1);
    }

    @Override
    public ItemStack removeItemNoUpdate(int p_18951_) {
        return removeItem(p_18951_, 1);
    }

    @Override
    public void setItem(
            int p_18944_,
            ItemStack p_18945_
    ) {
        journal.setItem(p_18944_, MCTownItem.fromMCItemStack(p_18945_));
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player p_18946_) {
        // TODO: I think this is another distance check?
        return false;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < journal.getCapacity(); i++) {
            journal.removeItem(i);
        }
    }
}
