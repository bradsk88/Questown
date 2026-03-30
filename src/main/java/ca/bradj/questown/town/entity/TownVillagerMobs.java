package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.integration.minecraft.TownStateSerializer;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.TownState;

import java.util.Optional;

import static ca.bradj.questown.town.entity.TownFlagState.NBT_TOWN_STATE;

public class TownVillagerMobs {
    public static void assumeStateFromTown(
            TownFlagBlockEntity town,
            VisitorMobEntity visitorMobEntity
    ) {
        if (!Compat.getBlockStoredTagData(town).contains(NBT_TOWN_STATE)) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but town state is missing. This is a bug and may cause unexpected behaviour.");
            return;
        }
        MCTownState state = TownStateSerializer.INSTANCE.load(
                Compat.getBlockStoredTagData(town)
                      .getCompound(NBT_TOWN_STATE),
                town.getServerLevel(),
                bp -> town.pois.getWelcomeMats().contains(bp)
        );
        Optional<TownState.VillagerData<MCHeldItem>> match = state.villagers.stream()
                                                                            .filter(v -> v.uuid.equals(visitorMobEntity.getUUID()))
                                                                            .findFirst();
        if (match.isEmpty()) {
            QT.FLAG_LOGGER.error(
                    "Villager entity exists but is not present on town state. This is a bug and may cause unexpected behaviour.");
            return;
        }
        town.villagerHandle.register(visitorMobEntity);
        TownState.VillagerData<MCHeldItem> m = match.get();
        visitorMobEntity.initialize(town, m.uuid, m.xPosition, m.yPosition, m.zPosition, m.journal);
    }
}
