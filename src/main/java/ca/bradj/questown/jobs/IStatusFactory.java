package ca.bradj.questown.jobs;

public interface IStatusFactory<STATUS extends IStatus<STATUS>> {
    STATUS droppingLoot();

    STATUS noSpace();

    STATUS goingToJobSite();

    STATUS noSupplies();

    STATUS collectingSupplies();

    STATUS idle();

    STATUS extractingProduct();

    STATUS relaxing();
}
