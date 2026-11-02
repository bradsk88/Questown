package ca.bradj.questown.jobs.requests;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestCategoriesTest {

    private static final ResourceLocation FOOD = new ResourceLocation("questown", "villager_food");
    private static final ResourceLocation SWORDS = new ResourceLocation("questown", "swords");

    private static final Map<String, Set<ResourceLocation>> MEMBERSHIP = Map.of(
            "cooked_mutton", Set.of(FOOD),
            "stone_sword", Set.of(SWORDS),
            "planks", Set.of()
    );

    @Test
    void oneCookableFoodIsEnoughToOfferFood() {
        assertEquals(List.of(FOOD),
                RequestCategories.offered(List.of("cooked_mutton", "planks"), (i, c) -> MEMBERSHIP.get(i).contains(c)));
    }

    @Test
    void categoriesComeInListOrder() {
        assertEquals(List.of(FOOD, SWORDS),
                RequestCategories.offered(List.of("stone_sword", "cooked_mutton"), (i, c) -> MEMBERSHIP.get(i).contains(c)));
    }

    @Test
    void nothingProducibleInAnyCategoryOffersNone() {
        assertEquals(List.of(),
                RequestCategories.offered(List.of("planks"), (i, c) -> MEMBERSHIP.get(i).contains(c)));
    }

    /** A product that is a whole offered category isn't also its own tile. */
    @Test
    void anOfferedCategoryTagIsNotAlsoAProduct() {
        Map<String, ResourceLocation> tags = Map.of("#villager_food", FOOD);
        assertEquals(List.of("bread", "stone_sword"),
                RequestCategories.withoutCategories(List.of("#villager_food", "bread", "stone_sword"),
                        tags::get, List.of(FOOD, SWORDS)));
    }

    @Test
    void aTagThatIsNotOfferedStaysAProduct() {
        Map<String, ResourceLocation> tags = Map.of("#swords", SWORDS);
        assertEquals(List.of("#swords"),
                RequestCategories.withoutCategories(List.of("#swords"), tags::get, List.of(FOOD)));
    }
}
