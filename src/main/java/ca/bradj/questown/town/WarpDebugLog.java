package ca.bradj.questown.town;

import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.Positions;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Utility class for verbose time warp debugging output.
 * Provides:
 * 1. Formatted container inventory display
 * 2. Before/after state comparison
 * 3. Warp event logging
 */
public class WarpDebugLog {

    // Thread-local instance for logging events from deep in the call stack
    private static final ThreadLocal<WarpDebugLog> CURRENT = new ThreadLocal<>();

    private final List<String> events = new ArrayList<>();
    private MCTownState beforeState;
    private boolean enabled = false;

    public WarpDebugLog() {
    }

    /**
     * Start a verbose warp session. Call this before warp begins.
     */
    public static WarpDebugLog start() {
        WarpDebugLog log = new WarpDebugLog();
        log.enabled = true;
        CURRENT.set(log);
        return log;
    }

    /**
     * End the current verbose warp session.
     */
    public static void end() {
        CURRENT.remove();
    }

    /**
     * Get the current verbose log, or null if not in a verbose session.
     */
    public static WarpDebugLog current() {
        return CURRENT.get();
    }

    /**
     * Log an event if verbose mode is active.
     * Safe to call even when not in verbose mode.
     */
    public static void event(String format, Object... args) {
        WarpDebugLog log = CURRENT.get();
        if (log != null && log.enabled) {
            log.logEvent(String.format(format, args));
        }
    }

    /**
     * Capture the "before" state for later comparison
     */
    public void captureBeforeState(MCTownState state) {
        this.beforeState = state;
    }

    /**
     * Log a warp event (e.g., "Villager collected 1 carrot")
     */
    public void logEvent(String event) {
        events.add(event);
    }

    /**
     * Format a single container's inventory for display
     */
    public static String formatContainer(ContainerTarget<MCContainer, MCTownItem> container) {
        BlockPos pos = Positions.ToBlock(container.getPosition(), container.getYPosition());
        Map<String, Integer> itemCounts = new LinkedHashMap<>();

        for (int i = 0; i < container.size(); i++) {
            MCTownItem item = container.getItem(i);
            if (!item.isEmpty()) {
                String name = item.getShortName();
                // getShortName returns "NxItem" format, extract just item name for grouping
                String itemName = name.contains("x") ? name.substring(name.indexOf("x") + 1) : name;
                int qty = item.quantity();
                itemCounts.merge(itemName, qty, Integer::sum);
            }
        }

        if (itemCounts.isEmpty()) {
            return String.format("  [%d, %d, %d]: (empty)", pos.getX(), pos.getY(), pos.getZ());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("  [%d, %d, %d]: ", pos.getX(), pos.getY(), pos.getZ()));
        List<String> items = new ArrayList<>();
        itemCounts.forEach((name, count) -> items.add(count + "x " + name));
        sb.append(String.join(", ", items));
        return sb.toString();
    }

    /**
     * Format all containers for display
     */
    public static String formatAllContainers(ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers) {
        if (containers.isEmpty()) {
            return "  (no containers)";
        }
        StringBuilder sb = new StringBuilder();
        for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
            sb.append(formatContainer(container)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Format a villager's inventory for display
     */
    public static String formatVillagerInventory(TownState.VillagerData<MCHeldItem> villager) {
        String uuid = UtilClean.truncateMiddle(villager.uuid);
        List<String> items = new ArrayList<>();
        for (MCHeldItem item : villager.journal.items()) {
            if (!item.isEmpty()) {
                items.add(item.getShortName());
            }
        }
        if (items.isEmpty()) {
            return String.format("  %s: (empty inventory)", uuid);
        }
        return String.format("  %s: %s", uuid, String.join(", ", items));
    }

    /**
     * Generate a comparison between before and after states
     */
    public String generateComparison(MCTownState afterState) {
        if (beforeState == null) {
            return "  (no before state captured)";
        }

        StringBuilder sb = new StringBuilder();

        // Compare container contents
        sb.append("Container Changes:\n");
        Map<BlockPos, Map<String, Integer>> beforeContents = getContainerContents(beforeState.containers);
        Map<BlockPos, Map<String, Integer>> afterContents = getContainerContents(afterState.containers);

        Set<BlockPos> allPositions = new HashSet<>();
        allPositions.addAll(beforeContents.keySet());
        allPositions.addAll(afterContents.keySet());

        boolean anyContainerChanges = false;
        for (BlockPos pos : allPositions) {
            Map<String, Integer> before = beforeContents.getOrDefault(pos, Map.of());
            Map<String, Integer> after = afterContents.getOrDefault(pos, Map.of());
            String diff = diffItemCounts(before, after);
            if (!diff.isEmpty()) {
                anyContainerChanges = true;
                sb.append(String.format("  [%d, %d, %d]: %s\n", pos.getX(), pos.getY(), pos.getZ(), diff));
            }
        }
        if (!anyContainerChanges) {
            sb.append("  (no changes)\n");
        }

        // Compare villager inventories
        sb.append("Villager Inventory Changes:\n");
        boolean anyVillagerChanges = false;
        for (int i = 0; i < Math.max(beforeState.villagers.size(), afterState.villagers.size()); i++) {
            Map<String, Integer> before = i < beforeState.villagers.size()
                    ? getVillagerItems(beforeState.villagers.get(i)) : Map.of();
            Map<String, Integer> after = i < afterState.villagers.size()
                    ? getVillagerItems(afterState.villagers.get(i)) : Map.of();
            String uuid = UtilClean.truncateMiddle(
                    i < afterState.villagers.size()
                            ? afterState.villagers.get(i).uuid
                            : beforeState.villagers.get(i).uuid
            );
            String diff = diffItemCounts(before, after);
            if (!diff.isEmpty()) {
                anyVillagerChanges = true;
                sb.append(String.format("  %s: %s\n", uuid, diff));
            }
        }
        if (!anyVillagerChanges) {
            sb.append("  (no changes)\n");
        }

        // Compare work states
        sb.append("Work State Changes:\n");
        boolean anyWorkChanges = false;
        Set<BlockPos> allWorkPositions = new HashSet<>();
        allWorkPositions.addAll(beforeState.workStates.keySet());
        allWorkPositions.addAll(afterState.workStates.keySet());

        for (BlockPos pos : allWorkPositions) {
            State beforeWs = beforeState.workStates.get(pos);
            State afterWs = afterState.workStates.get(pos);
            if (!Objects.equals(beforeWs, afterWs)) {
                anyWorkChanges = true;
                String beforeStr = beforeWs != null ? String.valueOf(beforeWs.processingState()) : "none";
                String afterStr = afterWs != null ? String.valueOf(afterWs.processingState()) : "none";
                sb.append(String.format("  [%d, %d, %d]: %s -> %s\n",
                        pos.getX(), pos.getY(), pos.getZ(), beforeStr, afterStr));
            }
        }
        if (!anyWorkChanges) {
            sb.append("  (no changes)\n");
        }

        return sb.toString().trim();
    }

    /**
     * Get all logged events
     */
    public List<String> getEvents() {
        return ImmutableList.copyOf(events);
    }

    /**
     * Format all events for display
     */
    public String formatEvents() {
        if (events.isEmpty()) {
            return "  (no events logged)";
        }
        StringBuilder sb = new StringBuilder();
        for (String event : events) {
            sb.append("  ").append(event).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Clear all logged events
     */
    public void clearEvents() {
        events.clear();
    }

    // Helper: extract container contents as position -> (itemName -> count) map
    private static Map<BlockPos, Map<String, Integer>> getContainerContents(
            ImmutableList<ContainerTarget<MCContainer, MCTownItem>> containers
    ) {
        Map<BlockPos, Map<String, Integer>> result = new HashMap<>();
        for (ContainerTarget<MCContainer, MCTownItem> container : containers) {
            BlockPos pos = Positions.ToBlock(container.getPosition(), container.getYPosition());
            Map<String, Integer> items = new LinkedHashMap<>();
            for (int i = 0; i < container.size(); i++) {
                MCTownItem item = container.getItem(i);
                if (!item.isEmpty()) {
                    String name = item.getShortName();
                    String itemName = name.contains("x") ? name.substring(name.indexOf("x") + 1) : name;
                    items.merge(itemName, item.quantity(), Integer::sum);
                }
            }
            result.put(pos, items);
        }
        return result;
    }

    // Helper: extract villager items as itemName -> count map
    private static Map<String, Integer> getVillagerItems(TownState.VillagerData<MCHeldItem> villager) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (MCHeldItem item : villager.journal.items()) {
            if (!item.isEmpty()) {
                String name = item.getShortName();
                String itemName = name.contains("x") ? name.substring(name.indexOf("x") + 1) : name;
                items.merge(itemName, item.quantity(), Integer::sum);
            }
        }
        return items;
    }

    // Helper: compute diff between two item count maps
    private static String diffItemCounts(Map<String, Integer> before, Map<String, Integer> after) {
        Set<String> allItems = new HashSet<>();
        allItems.addAll(before.keySet());
        allItems.addAll(after.keySet());

        List<String> changes = new ArrayList<>();
        for (String item : allItems) {
            int beforeCount = before.getOrDefault(item, 0);
            int afterCount = after.getOrDefault(item, 0);
            int delta = afterCount - beforeCount;
            if (delta != 0) {
                String sign = delta > 0 ? "+" : "";
                changes.add(sign + delta + " " + item);
            }
        }
        return String.join(", ", changes);
    }
}
