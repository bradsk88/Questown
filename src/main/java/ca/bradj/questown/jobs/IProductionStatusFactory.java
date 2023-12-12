package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.IProductionStatus;

public interface IProductionStatusFactory<
        STATUS extends IProductionStatus<STATUS>
> extends IStatusFactory<STATUS> {
    STATUS fromJobBlockState(int s);

    STATUS waitingForTimedState();
}
