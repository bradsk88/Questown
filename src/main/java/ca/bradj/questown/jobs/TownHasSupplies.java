package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Pair;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class TownHasSupplies<HELD, ITEM extends Item<ITEM>, C extends ContainerTarget<?, ITEM>> extends SimpleDependency {

    private final Function<Integer, PredicateCollection<HELD, HELD>> ingredients;
    private final Function<Integer, PredicateCollection<ITEM, ITEM>> tools;
    private final Supplier<ImmutableList<ContainersClean.JobSite<C>>> rooms;
    private final Supplier<? extends Map<Integer, ? extends RoomsWithWorkableStatefulBlocks<?>>> roomsHaveWorkableBlocks;
    private final Function<ITEM, HELD> convert;

    public TownHasSupplies(
            Function<Integer, PredicateCollection<HELD, HELD>> ingredients,
            Function<Integer, PredicateCollection<ITEM, ITEM>> tools,
            Supplier<ImmutableList<ContainersClean.JobSite<C>>> rooms,
            Supplier<? extends Map<Integer, ? extends RoomsWithWorkableStatefulBlocks<?>>> roomsHaveWorkableBlocks,
            Function<ITEM, HELD> convert
    ) {
        super("town has supplies");
        this.ingredients = ingredients;
        this.tools = tools;
        this.rooms = () -> rooms.get().stream().collect(ImmutableList.toImmutableList());
        this.roomsHaveWorkableBlocks = roomsHaveWorkableBlocks;
        this.convert = convert;
    }

    @Override
    public String describe() {
        return "TODO"; // TODO?
    }

    @Override
    protected Populated<WithReason<Boolean>> doPopulate(boolean stopOnTrue) {
        ImmutableMap.Builder<String, Object> b = ImmutableMap.builder();
        Map<Integer, ? extends LZCD.Dependency<Void>> needs = roomsHaveWorkableBlocks.get();
        b.put("room needs", needs);

        List<PredicateCollection<HELD, HELD>> neededIngredients = new ArrayList<>();
        List<PredicateCollection<ITEM, ITEM>> neededTools = new ArrayList<>();
        for (Map.Entry<Integer, ? extends LZCD.Dependency<Void>> v : needs.entrySet()) {
            Integer state = v.getKey();
            if (!v.getValue().apply(() -> null).value) {
                continue;
            }
            PredicateCollection<HELD, HELD> ingt = ingredients.apply(state);
            if (ingt != null) {
                neededIngredients.add(ingt);
            }
            PredicateCollection<ITEM, ITEM> tool = tools.apply(state);
            if (tool != null) {
                neededTools.add(tool);
            }

        }

        b.put("relevant ingredients", neededIngredients);
        b.put("relevant tools", neededTools);

        List<C> containers = ContainersClean.get(rooms.get(), false);

        b.put("containers", containers);

        @Nullable WithReason<Boolean> found = null;
        Map<String, Object> b2 = new HashMap<>();

        for (ContainerTarget<?, ITEM> c : containers) {

            Position position = Positions.FromBlockPos(c.getBlockPos());
            String dPos = position.getUIString();
            for (ITEM i : c.getItems()) {
                if (i.isEmpty()) {
                    continue;
                }
                if (b2.get(dPos) != null && Boolean.TRUE.equals(b2.get(dPos))) {
                    continue;
                }
                HELD iHeld = convert.apply(i);
                Optional<?> matchedIngredient = neededIngredients.stream().filter(ing -> ing.test(iHeld))
                                                                 .findFirst();
                String result = matchedIngredient.map(Object::toString).orElse("No match");
                b2.put(dPos, new Pair<>(result, c.toShortString(false)));
                if (matchedIngredient.isPresent()) {
                    found = WithReason.always(true, i.getShortName() + " matches " + matchedIngredient.get());
                    if (stopOnTrue) {
                        break;
                    }
                }
                Optional<?> matchedTool = neededTools.stream().filter(ing -> ing.test(i)).findFirst();
                result = matchedTool.map(Object::toString).orElse("No match");
                b2.put(dPos, new Pair<>(result, c.toShortString(false)));
                if (matchedTool.isPresent()) {
                    found = WithReason.always(true, i.getShortName() + " matches " + matchedTool.get());
                    if (stopOnTrue) {
                        break;
                    }
                }
            }
            if (found != null && stopOnTrue) {
                break;
            }
        }

        if (found == null) {
            found = WithReason.always(false, "No matches found for " + ingredients + " in any containers");
        }

        b.put("supply checks", ImmutableMap.copyOf(b2));
        b.put("predicate", ingredients);
        ImmutableMap<String, Object> build = b.build();
        return new Populated<>("town has supplies", found, build, null) {
            @Override
            protected String stringRep() {
                return "town has supplies [" + build + "]";
            }
        };
    }
}
