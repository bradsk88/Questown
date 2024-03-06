package ca.bradj.questown.jobs;

import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableMap;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

public class WorkStates {

    private final int maxState;
    private final ImmutableMap<Integer, Supplier<Ingredient>> ingredientsRequired;
    private final ImmutableMap<Integer, Supplier<Integer>> ingredientQtyRequired;
    private final ImmutableMap<Integer, Supplier<Ingredient>> toolsRequired;
    private final ImmutableMap<Integer, Supplier<Integer>> workRequired;
    private final ImmutableMap<Integer, Supplier<Integer>> timeRequired;
    private ImmutableMap<Integer, Ingredient> realizedTools;
    private ImmutableMap<Integer, Ingredient> realizedIngredients;
    private ImmutableMap<Integer, Integer> realizedQty;
    private ImmutableMap<Integer, Integer> realizedWork;
    private ImmutableMap<Integer, Integer> realizedTime;

    public WorkStates(
            int maxState,
            ImmutableMap<Integer, Supplier<Ingredient>> ingredientsRequired,
            ImmutableMap<Integer, Supplier<Integer>> ingredientQtyRequired,
            ImmutableMap<Integer, Supplier<Ingredient>> toolsRequired,
            ImmutableMap<Integer, Supplier<Integer>> workRequired,
            ImmutableMap<Integer, Supplier<Integer>> timeRequired
    ) {
        this.maxState = maxState;
        this.ingredientsRequired = ingredientsRequired;
        this.ingredientQtyRequired = ingredientQtyRequired;
        this.toolsRequired = toolsRequired;
        this.workRequired = workRequired;
        this.timeRequired = timeRequired;
    }

    public int maxState() {
        return maxState;
    }

    public ImmutableMap<Integer, Ingredient> toolsRequired() {
        if (this.realizedTools != null) {
            return this.realizedTools;
        }
        this.realizedTools = Util.realize(this.toolsRequired);
        return this.realizedTools;
    }

    public ImmutableMap<Integer, Ingredient> ingredientsRequired() {
        if (this.realizedIngredients != null) {
            return this.realizedIngredients;
        }
        this.realizedIngredients = Util.realize(this.ingredientsRequired);
        return this.realizedIngredients;
    }

    public ImmutableMap<Integer, Integer> ingredientQtyRequired() {
        if (this.realizedQty != null) {
            return this.realizedQty;
        }
        this.realizedQty = Util.realize(this.ingredientQtyRequired);
        return this.realizedQty;
    }

    public ImmutableMap<Integer, Integer> workRequired() {
        if (this.realizedWork != null) {
            return this.realizedWork;
        }
        this.realizedWork = Util.realize(this.workRequired);
        return this.realizedWork;
    }

    public ImmutableMap<Integer, Integer> timeRequired() {
        if (this.realizedTime != null) {
            return this.realizedTime;
        }
        this.realizedTime = Util.realize(this.timeRequired);
        return this.realizedTime;
    }
}
