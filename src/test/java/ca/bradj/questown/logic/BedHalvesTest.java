package ca.bradj.questown.logic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

class BedHalvesTest {

    private static final Predicate<String> IS_BED = s -> s.startsWith("bed");

    @Test
    void bedroom_isLightAndOneBed() {
        List<String> result = BedHalves.oneEntryPerBed(List.of("torch", "bed", "bed"), IS_BED);
        Assertions.assertEquals(List.of("torch", "bed"), result);
    }

    @Test
    void bigBedroom_isLightAndTwoBeds() {
        List<String> result = BedHalves.oneEntryPerBed(List.of("torch", "bed", "bed", "bed", "bed"), IS_BED);
        Assertions.assertEquals(List.of("torch", "bed", "bed"), result);
    }

    @Test
    void inn_isSixBedsAndKeepsTheSign() {
        List<String> inn = List.of("torch",
                "bed", "bed", "bed", "bed", "bed", "bed",
                "bed", "bed", "bed", "bed", "bed", "bed",
                "sign");
        List<String> result = BedHalves.oneEntryPerBed(inn, IS_BED);
        Assertions.assertEquals(List.of("torch", "bed", "bed", "bed", "bed", "bed", "bed", "sign"), result);
    }

    @Test
    void halvesSplitByOtherEntries_stillPair() {
        List<String> result = BedHalves.oneEntryPerBed(List.of("bed_a", "torch", "bed_b"), IS_BED);
        Assertions.assertEquals(List.of("bed_a", "torch"), result);
    }

    @Test
    void oddBedEntry_isKept() {
        List<String> result = BedHalves.oneEntryPerBed(List.of("torch", "bed"), IS_BED);
        Assertions.assertEquals(List.of("torch", "bed"), result);
    }

    @Test
    void noBeds_unchanged() {
        List<String> result = BedHalves.oneEntryPerBed(List.of("furnace", "torch"), IS_BED);
        Assertions.assertEquals(List.of("furnace", "torch"), result);
    }
}
