package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.workstatus.State;

public record HendlarInpoots<TOWN, POS, LEVEL>(
        AbstractStateInteraction<Inpoots<TOWN, LEVEL>, POS, ?, ?, TOWN> wi,
        Inpoots<TOWN, LEVEL> inState,
        ProductionStatus status,
        State workBlockState,
        int maxState,
        POS workSpotStandIn
) {
}
