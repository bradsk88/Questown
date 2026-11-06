package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

class TestTagResolverTest {

    @Test
    void literalItem_matchesOnlyItself() {
        Assertions.assertTrue(TestTagResolver.matches(new GathererJournalTest.TestItem("minecraft:cod"), "minecraft:cod"));
        Assertions.assertFalse(TestTagResolver.matches(new GathererJournalTest.TestItem("minecraft:beef"), "minecraft:cod"));
    }

    @Test
    void questownTag_membersExpandFromModDataFile() {
        Set<String> fishes = TestTagResolver.members("#questown:raw_fishes");
        Assertions.assertTrue(fishes.contains("minecraft:cod"));
        Assertions.assertTrue(fishes.contains("minecraft:salmon"));
        Assertions.assertFalse(fishes.contains("minecraft:beef"));
        Assertions.assertTrue(
                TestTagResolver.matches(new GathererJournalTest.TestItem("minecraft:salmon"), "#questown:raw_fishes"));
    }

    @Test
    void tagGate_vsTagItem_equalOrIntersectingTags() {
        Assertions.assertTrue(
                TestTagResolver.matches(new GathererJournalTest.TestItem("#minecraft:coals"), "#minecraft:coals"));
        Assertions.assertTrue(
                TestTagResolver.matches(new GathererJournalTest.TestItem("#questown:raw_ingredients"), "#questown:raw_fishes"));
        Assertions.assertFalse(
                TestTagResolver.matches(new GathererJournalTest.TestItem("#questown:axes"), "#questown:raw_fishes"));
    }

    @Test
    void literalGate_vsTagItem_memberOfItemTag() {
        Assertions.assertTrue(
                TestTagResolver.matches(new GathererJournalTest.TestItem("#minecraft:coals"), "minecraft:coal"));
        Assertions.assertFalse(
                TestTagResolver.matches(new GathererJournalTest.TestItem("#minecraft:coals"), "minecraft:beef"));
    }

    @Test
    void vanillaTag_vendoredCoals() {
        Assertions.assertEquals(
                Set.of("minecraft:coal", "minecraft:charcoal"),
                TestTagResolver.members("#minecraft:coals"));
    }

    @Test
    void nestedTagReferences_expandRecursively() {
        Set<String> raw = TestTagResolver.members("#questown:raw_ingredients");
        Assertions.assertTrue(raw.contains("minecraft:cod"), "nested #questown:raw_fishes should expand; got " + raw);
    }

    @Test
    void unresolvableTag_throwsInsteadOfLeavingTheGateVacuouslyUnsatisfiable() {
        Assertions.assertThrows(IllegalStateException.class, () -> TestTagResolver.members("#minecraft:not_a_real_tag"));
        Assertions.assertThrows(IllegalStateException.class, () -> TestTagResolver.members("#questown:not_a_real_tag"));
    }

    @Test
    void externalNamespaceTags_resolveFromDependencyJars() {
        // The forge tags ship in the Forge jar on the test classpath.
        Set<String> mushrooms = TestTagResolver.members("#forge:mushrooms");
        Assertions.assertTrue(mushrooms.contains("minecraft:brown_mushroom"));
        Assertions.assertTrue(mushrooms.contains("minecraft:red_mushroom"));
    }
}
