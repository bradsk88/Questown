package ca.bradj.questown.jobs;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.AbstractWorldInteraction;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.TownState;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class MCTownStateWorldInteraction extends AbstractWorldInteraction<MCTownState, BlockPos, MCTownItem, MCHeldItem, MCTownState> {
    private final BlockPos position;
    private final Supplier<MCHeldItem> result;

    public MCTownStateWorldInteraction(
            JobID jobId,
            int villagerIndex,
            int interval,
            int maxState,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            Supplier<MCHeldItem> result
    ) {
        super(
                jobId, villagerIndex, interval, maxState, Jobs.unMC(toolsRequiredAtStates),
                workRequiredAtStates, Jobs.unMCHeld(ingredientsRequiredAtStates),
                ingredientQuantityRequiredAtStates, timeRequiredAtStates
        );
        this.position = new BlockPos(villagerIndex, villagerIndex, villagerIndex);
        this.result = result;
    }

    @Override
    protected MCTownState setHeldItem(MCTownState uxtra, MCTownState tuwn, int villagerIndex, int itemIndex, MCHeldItem item) {
        if (tuwn == null) {
            tuwn = uxtra;
        }
        TownState.VillagerData<MCHeldItem> villager = tuwn.villagers.get(villagerIndex);
        return tuwn.withVillagerData(villagerIndex, villager.withSetItem(itemIndex, item));
    }

    @Override
    protected MCTownState degradeTool(MCTownState mcTownState, @Nullable MCTownState tuwn, Function<MCTownItem, Boolean> isExpectedTool) {
        if (tuwn == null) {
            tuwn = mcTownState;
        }

        int i = 0;
        for (MCHeldItem item : this.getHeldItems(mcTownState, villagerIndex)) {
            i++;
            if (!isExpectedTool.apply(item.get())) {
                continue;
            }
            ItemStack itemStack = item.get().toItemStack();
            itemStack.getItem().damageItem(itemStack ,1, null, e -> {}); // TODO: Get a reference to the villager?
            return setHeldItem(mcTownState, tuwn, villagerIndex, i, MCHeldItem.fromMCItemStack(itemStack));
        }
        return tuwn;
    }

    @Override
    protected boolean canInsertItem(MCTownState mcTownState, MCHeldItem item, BlockPos bp) {
        return true;
    }

    @Override
    protected ImmutableWorkStateContainer<BlockPos, MCTownState> getWorkStatuses(MCTownState mcTownState) {
        return mcTownState;
    }

    @Override
    protected Collection<MCHeldItem> getHeldItems(MCTownState mcTownState, int villagerIndex) {
        TownState.VillagerData<MCHeldItem> vil = mcTownState.villagers.get(villagerIndex);
        return vil.journal.items();
    }

    @Override
    protected MCTownState tryExtractOre(MCTownState mcTownState, BlockPos position) {
        AbstractWorkStatusStore.State s = getJobBlockState(mcTownState, position);
        if (s != null && s.processingState() == maxState) {
            int i = 0;
            for (MCHeldItem item : getHeldItems(mcTownState, villagerIndex)) {
                i++;
                if (item.isEmpty()) {
                    mcTownState = setHeldItem(mcTownState, mcTownState, villagerIndex, i, result.get());
                    return mcTownState.setJobBlockState(position, AbstractWorkStatusStore.State.fresh());
                }
            }
        }
        return null;
    }

    @Override
    protected boolean isEntityClose(MCTownState mcTownState, BlockPos position) {
        return true;
    }

    @Override
    protected boolean isReady(MCTownState mcTownState) {
        return true;
    }

    @Override
    public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
        return null;
    }
}
