package ca.bradj.questown.commands;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.WallDetection;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rooms.DoorTrouble;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

// Dev-only, read-only: a room drawn as text: '#' wall, 'D' door, '.' open floor, a letter per
// block (legend below). A door with no room yet gets a grid around it.
public class LookRoomCommand {

    private static final int PROBE_RADIUS = 4;
    private static final int MAX_SPAN = 24;

    public static void register(CommandDispatcher<CommandSourceStack> src) {
        // @formatter:off
        src.register(
            Commands.literal("_qtdev").then(
                Commands.literal("look_room")
                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(css -> run(css.getSource(),
                                BlockPosArgument.getLoadedBlockPos(css, "pos"))))
            )
        );
        // @formatter:on
    }

    private static int run(CommandSourceStack source, BlockPos doorPos) {
        TownFlagBlockEntity flag = firstFlag();
        if (flag == null) {
            QT.FLAG_LOGGER.warn("[look_room] no flag to look at a room for");
            return 1;
        }
        ServerLevel level = source.getLevel();
        DoorTrouble trouble = flag.getDeadDoors().get(doorPos);
        MCRoom room = roomForDoor(flag, doorPos);
        if (room == null) {
            QT.FLAG_LOGGER.info("[look_room] door {} has no detected room ({}); "
                            + "blocks around the door ('#' wall, 'D' door, '.' open, letter = block):",
                    doorPos.toShortString(), trouble == null ? "no trouble recorded" : trouble.name());
            printGrid(level,
                    doorPos.getX() - PROBE_RADIUS, doorPos.getX() + PROBE_RADIUS,
                    doorPos.getZ() - PROBE_RADIUS, doorPos.getZ() + PROBE_RADIUS,
                    doorPos.getY(), doorPos);
            return 0;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (InclusiveSpace s : room.getSpaces()) {
            minX = Math.min(minX, s.getWestX());
            maxX = Math.max(maxX, s.getEastX());
            minZ = Math.min(minZ, s.getNorthZ());
            maxZ = Math.max(maxZ, s.getSouthZ());
        }
        QT.FLAG_LOGGER.info("[look_room] room at door {}: y {}, x {}..{}, z {}..{}{} "
                        + "('#' wall, 'D' door, '.' open, letter = block):",
                doorPos.toShortString(), room.yCoord, minX, maxX, minZ, maxZ,
                trouble == null ? "" : ", trouble " + trouble.name());
        printGrid(level, minX, maxX, minZ, maxZ, room.yCoord, doorPos);
        return 0;
    }

    private static void printGrid(
            ServerLevel level, int minX, int maxX, int minZ, int maxZ, int y, BlockPos doorPos
    ) {
        if (maxX - minX + 1 > MAX_SPAN || maxZ - minZ + 1 > MAX_SPAN) {
            QT.FLAG_LOGGER.warn("[look_room] area too big to print (x {}..{}, z {}..{})",
                    minX, maxX, minZ, maxZ);
            return;
        }
        Map<Character, String> legend = new LinkedHashMap<>();
        QT.FLAG_LOGGER.info("[look_room]   x {}..{} (west to east)", minX, maxX);
        for (int z = minZ; z <= maxZ; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = minX; x <= maxX; x++) {
                row.append(cellChar(level, x, y, z, doorPos, legend));
            }
            QT.FLAG_LOGGER.info("[look_room]   z={}: {}", z, row);
        }
        for (Map.Entry<Character, String> e : legend.entrySet()) {
            QT.FLAG_LOGGER.info("[look_room]   '{}' = {}", e.getKey(), e.getValue());
        }
    }

    private static char cellChar(
            ServerLevel level, int x, int y, int z, BlockPos doorPos, Map<Character, String> legend
    ) {
        if (x == doorPos.getX() && z == doorPos.getZ()) {
            return 'D';
        }
        if (WallDetection.IsWall(level, new Position(x, z), y)) {
            return '#';
        }
        BlockPos bp = new BlockPos(x, y, z);
        if (level.getBlockState(bp).isAir() && level.getBlockState(bp.above()).isAir()) {
            return '.';
        }
        ResourceLocation id = Loader.blockIdOf(
                level.getBlockState(bp).getBlock());
        String path = id == null ? "unknown" : id.toString();
        char c = Character.toLowerCase(path.charAt(path.indexOf(':') + 1));
        while (legend.containsKey(c) && !legend.get(c).equals(path)) {
            c = c == 'z' ? 'A' : (char) (c + 1);
        }
        legend.putIfAbsent(c, path);
        return c;
    }

    private static @Nullable MCRoom roomForDoor(TownFlagBlockEntity flag, BlockPos doorPos) {
        for (MCRoom r : flag.getRoomHandle().getRegisteredRooms().getAllRooms()) {
            if (r.getDoorPos().x == doorPos.getX() && r.getDoorPos().z == doorPos.getZ()) {
                return r;
            }
        }
        return null;
    }

    private static @Nullable TownFlagBlockEntity firstFlag() {
        for (TownFlagBlockEntity f : ca.bradj.questown.town.entity.TownFlags.all()) {
            if (f.getBlockPos() != null) {
                return f;
            }
        }
        return null;
    }
}
