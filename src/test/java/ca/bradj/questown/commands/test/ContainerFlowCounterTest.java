package ca.bradj.questown.commands.test;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContainerFlowCounterTest {

    @Test
    void recordInsertAccumulatesPerDay() {
        ContainerFlowCounter c = new ContainerFlowCounter();
        c.recordInsert("minecraft:cooked_mutton", 0, 4);
        c.recordInsert("minecraft:cooked_mutton", 0, 2);
        c.recordInsert("minecraft:cooked_mutton", 1, 3);
        assertEquals(9, c.totalProduced("minecraft:cooked_mutton"));
        assertEquals(0, c.totalConsumed("minecraft:cooked_mutton"));
        Map<Long, Long> days = c.produced().get("minecraft:cooked_mutton");
        assertEquals(6L, days.get(0L));
        assertEquals(3L, days.get(1L));
    }

    @Test
    void recordExtractAccumulatesPerDay() {
        ContainerFlowCounter c = new ContainerFlowCounter();
        c.recordExtract("minecraft:cooked_mutton", 0, 5);
        c.recordExtract("minecraft:cooked_mutton", 1, 1);
        assertEquals(6, c.totalConsumed("minecraft:cooked_mutton"));
        assertEquals(0, c.totalProduced("minecraft:cooked_mutton"));
    }

    @Test
    void recordInsertIgnoresNonPositive() {
        ContainerFlowCounter c = new ContainerFlowCounter();
        c.recordInsert("x", 0, 0);
        c.recordInsert("x", 0, -1);
        assertEquals(0, c.totalProduced("x"));
        assertTrue(c.produced().isEmpty());
    }

    @Test
    void takeStockSnapshotReplaces() {
        ContainerFlowCounter c = new ContainerFlowCounter();
        c.takeStockSnapshot(Map.of("a", 1, "b", 2));
        assertEquals(1, c.stockOf("a"));
        assertEquals(2, c.stockOf("b"));
        c.takeStockSnapshot(Map.of("a", 0));
        assertEquals(0, c.stockOf("a"));
        assertEquals(0, c.stockOf("b"));
    }

    @Test
    void reportLinesAreDeterministicAndEmptyForEmptyCounter() {
        ContainerFlowCounter c = new ContainerFlowCounter();
        assertTrue(c.reportLines().isEmpty());
    }

    @Test
    void reportLinesSortItemsAndDays() {
        ContainerFlowCounter c = new ContainerFlowCounter();
        c.recordInsert("z_item", 5, 2);
        c.recordInsert("a_item", 3, 1);
        c.recordInsert("a_item", 1, 4);
        c.recordExtract("m_item", 1, 3);
        c.takeStockSnapshot(Map.of("a_item", 3, "z_item", 7));
        List<String> lines = c.reportLines();
        assertEquals(3, lines.size());
        // Sorted by item: a_item, m_item, z_item.
        assertTrue(lines.get(0).startsWith("[village] flow a_item"));
        assertTrue(lines.get(1).startsWith("[village] flow m_item"));
        assertTrue(lines.get(2).startsWith("[village] flow z_item"));
        // a_item produced days ascending: d1:4, d3:1.
        assertTrue(lines.get(0).contains("produced=d1:4,d3:1"));
        assertTrue(lines.get(0).contains("stock=3"));
        // m_item consumed only.
        assertTrue(lines.get(1).contains("consumed=d1:3"));
        assertTrue(lines.get(1).contains("stock=0"));
    }

    @Test
    void putStockMergesCounts() {
        Map<String, Integer> stock = new java.util.HashMap<>();
        ContainerFlowCounter.putStock(stock, "x", 2);
        ContainerFlowCounter.putStock(stock, "x", 3);
        ContainerFlowCounter.putStock(stock, "y", 1);
        assertEquals(5, stock.get("x"));
        assertEquals(1, stock.get("y"));
        // Non-positive counts are ignored.
        ContainerFlowCounter.putStock(stock, "z", 0);
        assertNull(stock.get("z"));
    }
}
