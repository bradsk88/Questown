package ca.bradj.questown.jobs.declarative;

public record InsertResult<TOWN, ITEM>(
        TOWN contextAfterInsert,
        ITEM itemBeforeInsert
) {
}
