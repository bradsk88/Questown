package ca.bradj.questown.town.containers;

import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.TownFlagBlockEntity;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TownContainersV2 {
    public static ImmutableList<ContainerTargetV2<MCRoom, MCContainer, MCTownItem>> findMatching(
            TownFlagBlockEntity townFlagBlockEntity,
            Predicate<MCTownItem> isWorkResult
    ) {
        // TODO: This could be more efficient

        Stream<ContainerTarget<MCContainer, MCTownItem>> old = TownContainers.findAllMatching(
                townFlagBlockEntity, isWorkResult::test);
        ImmutableList<MCRoom> rooms = townFlagBlockEntity.getRoomHandle()
                                                         .getAllRooms();

        ImmutableList.Builder<ContainerTargetV2<MCRoom, MCContainer, MCTownItem>> b = ImmutableList.builder();
        old.forEach(c -> {
            Optional<MCRoom> first = rooms.stream()
                                          .filter(v -> v.yCoord == c.getYPosition() && InclusiveSpaces.contains(
                                                  v.getSpaces(), c.getPosition()
                                          ))
                                          .findFirst();
            first.ifPresent(room -> b.add(new ContainerTargetV2<>(
                    room,
                    c.getPosition(), c.getYPosition(), c.getInteractPosition(),
                    c.getContainer(),
                    c::isStillValid
            )));
        });

        return b.build();
    }
}
