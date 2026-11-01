package ca.bradj.questown.town.names;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Golden-file test for {@link TownieNames#nameFromPath(String)}.
 *
 * <p>The curated golden file holds real {@code minecraft:} item paths and their
 * expected names; a change to the stoplist or filters regenerates a diff against
 * it, so regressions (e.g. a townie named *Tarnished*) are caught here rather
 * than in play. The pure extractor is asserted directly, so no registry
 * bootstrap is required.
 */
class TownieNamesTest {

    @Test
    void goldenPoolMatches() {
        List<String> lines = readLines("/townie_names_golden.txt");
        int asserted = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            String path = parts[0];
            String expected = parts.length >= 2 ? parts[1] : null;
            assertEquals(expected, TownieNames.nameFromPath(path), "name for path " + path);
            asserted++;
        }
        assertNotNull(lines, "golden file read");
    }

    @Test
    void digitTokenIsRejected() {
        assertEquals("Bulbs", TownieNames.nameFromPath("1000_bulbs"));
        assertNull(TownieNames.nameFromPath("1000"), "a lone digit token is rejected");
    }

    @Test
    void lengthFilterBounds() {
        assertNull(TownieNames.nameFromPath("ax"), "2-char token is too short");
        assertNull(TownieNames.nameFromPath("cobblestone"), "11-char token is too long");
        assertEquals("Amethyst", TownieNames.nameFromPath("amethyst"), "8-char token is kept");
    }

    @Test
    void stoplistCollapsesToCore() {
        assertEquals("Copper", TownieNames.nameFromPath("waxed_oxidized_cut_copper_stairs"));
        assertEquals("Oak", TownieNames.nameFromPath("oak_hanging_sign"));
        assertEquals("Copper", TownieNames.nameFromPath("cut_copper_stairs"));
    }

    @Test
    void blocklistRejectsBadResults() {
        assertNull(TownieNames.nameFromPath("rotten_flesh"));
        assertNull(TownieNames.nameFromPath("poisonous_potato"));
    }

    @Test
    void namespaceGateBlocksNonMinecraft() {
        assertEquals("Diamond", TownieNames.nameForItem(new ResourceLocation("minecraft", "diamond")));
        assertNull(TownieNames.nameForItem(new ResourceLocation("some_mod", "diamond")));
    }

    private static List<String> readLines(String resource) {
        List<String> out = new ArrayList<>();
        try (InputStream in = TownieNamesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "missing golden file " + resource);
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                out.add(line);
            }
        } catch (Exception e) {
            fail("could not read " + resource + ": " + e);
        }
        return out;
    }
}
