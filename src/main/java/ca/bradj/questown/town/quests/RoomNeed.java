package ca.bradj.questown.town.quests;

import ca.bradj.questown.town.econ.NoMCEconomics;

public record RoomNeed<ID>(
        ID id,
        int timesNeeded,
        int villagersWhoNeed
) implements NoMCEconomics.Needable {
    public RoomNeed<ID> withVillagers(int i) {
        return new RoomNeed<>(id(), timesNeeded(), i);
    }

    public RoomNeed<ID> withTimes(int i) {
        return new RoomNeed<>(id(), i, villagersWhoNeed());
    }

    @Override
    public String toString() {
        return "RoomNeed{" +
                "id=" + id +
                ", timesNeeded=" + timesNeeded +
                ", villagersWhoNeed=" + villagersWhoNeed +
                '}';
    }
}
