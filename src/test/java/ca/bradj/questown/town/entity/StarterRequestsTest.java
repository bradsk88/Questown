package ca.bradj.questown.town.entity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

class StarterRequestsTest {

    private static final Set<String> FOOD = Set.of("cooked_mutton", "cooked_beef", "bread");

    private static final BiPredicate<String, String> COVERS =
            (a, b) -> a.equals("#food") && FOOD.contains(b);

    @Test
    void onboardingCook_postsTheFoodTagAndNotTheMuttonItCovers() {
        List<String> declared = List.of("#food", "#food", "cooked_mutton");

        Assertions.assertEquals(List.of("#food"), StarterRequests.toPost(declared, COVERS));
    }

    @Test
    void anItemNoTagCovers_isStillPosted() {
        List<String> declared = List.of("#food", "cooked_cod_not_food");

        Assertions.assertEquals(
                List.of("#food", "cooked_cod_not_food"),
                StarterRequests.toPost(declared, COVERS)
        );
    }

    @Test
    void aCoveringTagDeclaredLater_stillDropsTheItem() {
        List<String> declared = List.of("cooked_mutton", "#food");

        Assertions.assertEquals(List.of("#food"), StarterRequests.toPost(declared, COVERS));
    }

    @Test
    void noTags_postsEveryDistinctItemInOrder() {
        List<String> declared = List.of("cooked_beef", "bread", "cooked_beef");

        Assertions.assertEquals(List.of("cooked_beef", "bread"), StarterRequests.toPost(declared, COVERS));
    }

    @Test
    void aRequestTheBoardAlreadyCovers_isNotPosted() {
        List<String> declared = List.of("#food", "cooked_mutton");
        Assertions.assertEquals(List.of(), StarterRequests.toPost(List.of("cooked_mutton"), List.of("#food"), COVERS));
        Assertions.assertEquals(List.of("#food"), StarterRequests.toPost(declared, List.of("#raw"), COVERS));
    }
}
