package ca.bradj.questown.town;

import ca.bradj.questown.QT;
import ca.bradj.questown.jobs.JobsRegistry;
import ca.bradj.questown.town.interfaces.JobHandle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SignBlock;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TownJobHandle implements JobHandle {

    public record State(
            int processingState,
            int ingredientCount,
            int workLeft
    ) {
        public State setProcessing(int s) {
            return new State(s, ingredientCount, workLeft);
        }

        public State setWorkLeft(int newVal) {
            return new State(processingState, ingredientCount, newVal);
        }

        public State setCount(int count) {
            return new State(processingState, count, workLeft);
        }
    }

    private final HashSet<MCRoom> rooms = new HashSet<>();
    private final HashMap<BlockPos, State> jobStatuses = new HashMap<>();

    int curIdx = 0;

    public TownJobHandle() {
    }

    @Override
    public State getJobBlockState(BlockPos bp) {
        return jobStatuses.get(bp);
    }

    @Override
    public void setJobBlockState(
            BlockPos bp,
            State bs
    ) {
        jobStatuses.put(bp, bs);
    }

    public interface InsertionRules {
        Map<Integer, Ingredient> ingredientsRequiredAtStates();

        Map<Integer, Integer> ingredientQuantityRequiredAtStates();
    }

    @Override
    public boolean tryInsertItem(
            InsertionRules rules,
            ItemStack item,
            BlockPos bp,
            int workToNextStep
    ) {
        State oldState = getJobBlockState(bp);
        int curValue = oldState.processingState();
        boolean canDo = false;
        Ingredient ingredient = rules.ingredientsRequiredAtStates().get(curValue);
        if (ingredient != null) {
            canDo = ingredient.test(item);
        }
        Integer qtyRequired = rules.ingredientQuantityRequiredAtStates().getOrDefault(curValue, 0);
        if (qtyRequired == null) {
            qtyRequired = 0;
        }
        int curCount = oldState.ingredientCount();
        if (canDo && curCount >= qtyRequired) {
            QT.BLOCK_LOGGER.error("Somehow exceeded required quantity: can accept up to {}, had {}", qtyRequired, curCount);
        }

        if (canDo) {
            item.shrink(1);
            int count = curCount + 1;
            State blockState = oldState.setCount(count);
            if (count < qtyRequired) {
                setJobBlockState(bp, blockState);
                return true;
            }
            int val = curValue + 1;
            blockState = blockState.setProcessing(val);
            blockState = blockState.setWorkLeft(workToNextStep);
            blockState = blockState.setCount(0);
            setJobBlockState(bp, blockState);
            return true;
        }
        return false;
    }

    @Override
    public boolean canInsertItem(
            ItemStack item,
            BlockPos bp
    ) {
        return jobStatuses.containsKey(bp);
    }

    public void tick(ServerLevel sl, Collection<MCRoom> allRooms) {
        rooms.addAll(allRooms);

        curIdx = (curIdx + 1) % rooms.size();

        this.doTick(sl, (MCRoom) rooms.toArray()[curIdx]);
    }

    private void doTick(
            ServerLevel sl,
            MCRoom o
    ) {
        for (InclusiveSpace s : o.getSpaces()) {
            for (Position p : InclusiveSpaces.getAllEnclosedPositions(s)) {
                BlockPos pp = Positions.ToBlock(p, o.yCoord);
                if (jobStatuses.containsKey(pp)) {
                    continue;
                }
                Block b = sl.getBlockState(pp).getBlock();
                if (JobsRegistry.isJobBlock(b)) {
                    int initialProcessingState = 0;
                    if (b instanceof SignBlock) {
                        // FIXME: If we add a job board block, we can have it
                        //  self-set its own processing state as jobs are added
                        initialProcessingState = 1;
                    }
                    State bs = new State(initialProcessingState, 0, 0);
                    jobStatuses.put(pp, bs);
                }
            }
        }
    }
}
