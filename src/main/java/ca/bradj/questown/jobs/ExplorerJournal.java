package ca.bradj.questown.jobs;

public class ExplorerJournal<I extends Item<I>, H extends HeldItem<H, I> & Item<H>> extends GathererJournal<I, H> {

    public ExplorerJournal(
            SignalSource sigs,
            EmptyFactory<H> ef,
            GathererStatuses.TownStateProvider cont,
            int inventoryCapacity
    ) {
        super(sigs, ef, cont, inventoryCapacity, (a) -> new Tools(false, false, false, false));
    }

    @Override
    public Snapshot<H> getSnapshot(EmptyFactory<H> ef) {
        return new Snapshot<>(ExplorerJob.ID, getStatus(), getItems());
    }
}