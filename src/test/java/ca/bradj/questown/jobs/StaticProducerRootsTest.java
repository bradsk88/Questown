package ca.bradj.questown.jobs;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision 3 (gannet top-need handoff, 2026-10-09): the unlock-quest producer
 * lookup is omniscient — the job definitions' declared product
 * ({@code initial_request}), not the discovery-gated
 * {@code ServerJobsRegistry.getResults} path. An unlock quest must be able to
 * ask for a producer the town has not discovered or unlocked yet; the discovery
 * gate stays on the job engine's execution path only.
 * <p>
 * Tests the pure core ({@link ServerJobsRegistry#staticProducerRootsFor}):
 * the declared products are handed in as data, because building them from the
 * live {@code Works} registry is Forge-registry-bound (covered by compile +
 * held run, the {@code Work.isUnlockedInitially} precedent).
 * <p>
 * TAG MEMBERSHIP is not unit-testable here and is not asserted: vanilla tag
 * <em>contents</em> are loaded at server data-pack load, not by
 * {@code Bootstrap.bootStrap()}, so an {@code Ingredient} built from a tag key
 * matches nothing in a plain unit test (proven 2026-10-09: the two
 * {@code @Disabled} cases below returned {@code []} where the tag member was
 * expected). The item↔item membership of the same {@code matches} path is
 * green above; the tag direction (e.g. need {@code #minecraft:coals} vs
 * declared {@code minecraft:coal}) is the production shape and is settled by
 * the held-run re-drive (tag labels already register correctly in runs 13/14,
 * live).
 */
class StaticProducerRootsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ServerJobsRegistry.ProducerDeclaration declares(String root, Item product) {
        return new ServerJobsRegistry.ProducerDeclaration(
                new JobID(root, root + "_job"),
                product == null ? null : Ingredient.of(product));
    }

    @Test
    void declaredProductMatchesTheNeed() {
        assertEquals(
                Set.of("miner"),
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(declares("miner", Items.COAL)),
                        Ingredient.of(Items.COAL)
                )
        );
    }

    @Disabled("Vanilla tag CONTENTS are loaded at server data-pack load, not by "
            + "Bootstrap.bootStrap() — the tag ingredient matches nothing in a plain unit "
            + "test (returned [] where [miner] was expected, proven 2026-10-09). "
            + "Production shape (need #minecraft:coals vs declared minecraft:coal); "
            + "settled by the held-run re-drive.")
    @Test
    void needTagMatchesAJobDeclaringAMemberItem() {
        // The need can be registered as a tag (#minecraft:coals) while the job
        // declares one concrete member (minecraft:coal) — membership is the
        // whole point of the Ingredient.test() match.
        assertEquals(
                Set.of("miner"),
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(declares("miner", Items.COAL)),
                        Ingredient.of(ItemTags.COALS)
                )
        );
    }

    @Disabled("Vanilla tag CONTENTS are loaded at server data-pack load, not by "
            + "Bootstrap.bootStrap() — the tag ingredient matches nothing in a plain unit "
            + "test (returned [] where [planksman] was expected, proven 2026-10-09).")
    @Test
    void declaredProductTagMatchesACreteNeed() {
        assertEquals(
                Set.of("planksman"),
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(new ServerJobsRegistry.ProducerDeclaration(
                                        new JobID("planksman", "planks"), Ingredient.of(ItemTags.PLANKS))),
                        Ingredient.of(Items.OAK_PLANKS)
                )
        );
    }

    @Test
    void jobsThatDoNotDeclareTheItemAreExcluded() {
        assertTrue(
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(declares("cook", Items.BREAD)),
                        Ingredient.of(Items.COAL)
                ).isEmpty()
        );
    }

    @Test
    void aJobNamingNoProductMatchesNothing() {
        assertTrue(
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(declares("organizer", null)),
                        Ingredient.of(Items.COAL)
                ).isEmpty()
        );
    }

    @Test
    void severalJobsUnderOneRootCollapseToTheRoot() {
        assertEquals(
                Set.of("miner"),
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(
                                declares("miner", Items.COAL),
                                new ServerJobsRegistry.ProducerDeclaration(new JobID("miner", "coal"), Ingredient.of(Items.COAL)),
                                new ServerJobsRegistry.ProducerDeclaration(new JobID("miner", "full_day"), Ingredient.of(Items.IRON_INGOT))
                        ),
                        Ingredient.of(Items.COAL)
                )
        );
    }

    @Test
    void noDeclarationsYieldsNoProducers() {
        assertTrue(
                ServerJobsRegistry.staticProducerRootsFor(
                        List.of(),
                        Ingredient.of(Items.COAL)
                ).isEmpty()
        );
    }
}
