package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.questown.town.workstatus.State;

public record HendlarInpoots<TOWN>(
        AbstractStateInteraction<Inpoots<TOWN>, TownPosition, ?, ?, TOWN> wi,
        Inpoots<TOWN> inState,
        ProductionStatus status,
        State workBlockState,
        int maxState,
        int villagerIndex
) {
}
