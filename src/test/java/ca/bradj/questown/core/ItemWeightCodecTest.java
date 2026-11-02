package ca.bradj.questown.core;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.InMemoryFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

// The item weights are stored as one string so they survive a TOML load and save.
class ItemWeightCodecTest {

    private static Config sampleWeights() {
        Config weights = Config.of(InMemoryFormat.defaultInstance());
        weights.add("minecraft:crafting_table", 22);
        weights.add("#minecraft:beds", 23);
        weights.add("minecraft:torch", 17);
        weights.add("questown:plate_block", Integer.MAX_VALUE);
        return weights;
    }

    @Test
    void encodeThenDecodeRoundTrips() {
        Config weights = sampleWeights();
        String encoded = ItemWeightCodec.encode(weights);
        Map<String, Integer> decoded = ItemWeightCodec.decode(encoded);

        Assertions.assertEquals(22, decoded.get("minecraft:crafting_table"));
        Assertions.assertEquals(23, decoded.get("#minecraft:beds"));
        Assertions.assertEquals(17, decoded.get("minecraft:torch"));
        Assertions.assertEquals(Integer.MAX_VALUE, decoded.get("questown:plate_block"));
    }

    @Test
    void decodeEmptyOrNullIsEmptyMap() {
        Assertions.assertTrue(ItemWeightCodec.decode("").isEmpty());
        Assertions.assertTrue(ItemWeightCodec.decode(null).isEmpty());
    }

    @Test
    void decodeSkipsMalformedPairs() {
        // A pair without '=' is skipped; the others survive.
        Map<String, Integer> decoded = ItemWeightCodec.decode("minecraft:torch=17;garbage;#minecraft:beds=23");
        Assertions.assertEquals(17, decoded.get("minecraft:torch"));
        Assertions.assertEquals(23, decoded.get("#minecraft:beds"));
        Assertions.assertEquals(2, decoded.size());
    }
}
