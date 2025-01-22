package ca.bradj.questown.town.quests;

public record RoomNeed<ID>(
        ID id,
        int villagersWhoNeed
) {
}
