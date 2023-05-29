package ca.bradj.questown.town;

import ca.bradj.questown.logic.TownCycle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.recipes.ActiveRecipes;
import ca.bradj.roomrecipes.recipes.RecipeDetection;
import ca.bradj.roomrecipes.recipes.RoomRecipe;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TownRoomsMap implements TownRooms.RecipeRoomChangeListener {
    private final Map<Integer, TownRooms> activeRooms = new HashMap<>();
    private final Map<Integer, ActiveRecipes<ResourceLocation>> activeRecipes = new HashMap<>();
    private int scanLevel = 0;
    private int scanBuffer = 0;
    private TownFlagBlockEntity changeListener;

    private void updateActiveRooms(
            Level level,
            BlockPos blockPos,
            int scanLevel
    ) {
        TownRooms ars = getOrCreateRooms(scanLevel, blockPos.getY());

        ImmutableMap<Position, Optional<Room>> rooms = TownCycle.findRooms(
                Positions.FromBlockPos(blockPos), ars
        );
        ars.update(rooms);

        ars.getAll().forEach(room -> {
            Optional<RoomRecipe> recipe = RecipeDetection.getActiveRecipe(level, room, ars, blockPos.getY());
            activeRecipes.get(scanLevel).update(room.getDoorPos(), recipe.map(RoomRecipe::getId).orElse(null));
        });
    }

    private TownRooms getOrCreateRooms(int scanLevel, int yCoord) {
        if (!activeRecipes.containsKey(scanLevel)) {
            ActiveRecipes<ResourceLocation> v = new ActiveRecipes<>();
            activeRecipes.put(scanLevel, v);
            v.addChangeListener(changeListener);
        }

        if (!activeRooms.containsKey(scanLevel)) {
            TownRooms v = new TownRooms(scanLevel, changeListener); // TODO: Consider using listener instead of passing entity
            activeRooms.put(scanLevel, v);
            v.setRecipeRoomChangeListener(this);
        }

        return activeRooms.get(scanLevel);
    }

    public void tick(
            ServerLevel level,
            BlockPos blockPos
    ) {

        scanBuffer = (scanBuffer + 1) % 2;
        int scanLevel = 0;
        if (scanBuffer == 0) {
            scanLevel = (scanLevel + 1) % 5;
            scanLevel = scanLevel + 1;
        }
        updateActiveRooms(level, blockPos, 0);

        if (scanLevel != 0) {
            int y = 2 * scanLevel;
            updateActiveRooms(level, blockPos.offset(0, y, 0), scanLevel);
        }
    }

    public void initialize(Map<Integer, ActiveRecipes<ResourceLocation>> ars) {
        if (this.activeRecipes.size() > 0) {
            throw new IllegalStateException("Double initialization");
        }
        this.activeRecipes.putAll(ars);
    }

    /**
     * @deprecated Used for a migration only.
     */
    public ActiveRecipes<ResourceLocation> get(int i) {
        return activeRecipes.get(i);
    }

    public void addChangeListener(TownFlagBlockEntity townFlagBlockEntity) {
        this.changeListener = townFlagBlockEntity; // TODO: Interface
    }

    public Collection<Room> getAllRooms() {
        return this.activeRooms.values().stream().map(TownRooms::getAll).flatMap(Collection::stream).toList();
    }

    @Override
    public void updateRecipeForRoom(int scanLevel, Position doorPos, @Nullable ResourceLocation resourceLocation) {
        this.activeRecipes.get(scanLevel).update(doorPos, resourceLocation);
    }
}
