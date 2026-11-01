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
 * The unlock-quest producer lookup is omniscient: the job definitions' declared product
 * ({@code initial_request}), not the discovery-gated {@code ServerJobsRegistry.getResults} path,
 * so an unlock quest can ask for a producer the town has not discovered or unlocked yet.
 * <p>
 * Tests the pure core ({@link ServerJobsRegistry#staticProducerRootsFor}) with the declared
 * products handed in as data. Tag membership is not asserted: tag contents load with the data
 * pack, not {@code Bootstrap.bootStrap()}, so the {@code @Disabled} tag cases match nothing here.
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
