package ca.bradj.questown.jobs;

import java.util.Objects;
import java.util.UUID;

public class Inpoots<TOWN, LEVEL> {
    private final TOWN town;
    private final LEVEL level;
    private final UUID villagerUUID;

    public Inpoots(
            TOWN town,
            LEVEL level,
            UUID villagerUUID
    ) {
        this.town = town;
        this.level = level;
        this.villagerUUID = villagerUUID;
    }

    public TOWN town() {
        return town;
    }

    public LEVEL level() {
        return level;
    }

    public UUID villagerUUID() {
        return villagerUUID;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Inpoots) obj;
        return Objects.equals(this.town, that.town) &&
                Objects.equals(this.level, that.level) &&
                Objects.equals(this.villagerUUID, that.villagerUUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(town, level, villagerUUID);
    }

    @Override
    public String toString() {
        return "Inpoots[" +
                "town=" + town + ", " +
                "level=" + level + ", " +
                "villagerUUID=" + villagerUUID + ']';
    }

}
