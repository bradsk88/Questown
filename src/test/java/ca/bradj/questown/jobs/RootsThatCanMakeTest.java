package ca.bradj.questown.jobs;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RootsThatCanMakeTest {

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
                ServerJobsRegistry.rootsThatCanMake(
                        List.of(declares("miner", Items.COAL)),
                        Ingredient.of(Items.COAL)
                )
        );
    }

    @Test
    void jobsThatDoNotDeclareTheItemAreExcluded() {
        assertTrue(
                ServerJobsRegistry.rootsThatCanMake(
                        List.of(declares("cook", Items.BREAD)),
                        Ingredient.of(Items.COAL)
                ).isEmpty()
        );
    }

    @Test
    void aJobNamingNoProductMatchesNothing() {
        assertTrue(
                ServerJobsRegistry.rootsThatCanMake(
                        List.of(declares("organizer", null)),
                        Ingredient.of(Items.COAL)
                ).isEmpty()
        );
    }

    @Test
    void severalJobsUnderOneRootCollapseToTheRoot() {
        assertEquals(
                Set.of("miner"),
                ServerJobsRegistry.rootsThatCanMake(
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
                ServerJobsRegistry.rootsThatCanMake(
                        List.of(),
                        Ingredient.of(Items.COAL)
                ).isEmpty()
        );
    }
}
