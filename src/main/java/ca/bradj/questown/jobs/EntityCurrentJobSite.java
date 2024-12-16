package ca.bradj.questown.jobs;

public record EntityCurrentJobSite<ROOM>(
        ROOM room,
        boolean isFarm
) {
}
