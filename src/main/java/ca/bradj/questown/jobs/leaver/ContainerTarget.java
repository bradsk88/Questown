package ca.bradj.questown.jobs.leaver;

import ca.bradj.questown.blocks.entity.ItemAccepting;
import ca.bradj.questown.jobs.Item;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ContainerTarget<C extends ContainerTarget.Container<I>, I extends Item<I>> {

    public static net.minecraft.world.Container REMOVED = new net.minecraft.world.Container() {
        @Override
        public int getContainerSize() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public ItemStack getItem(int p_18941_) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(
                int p_18942_,
                int p_18943_
        ) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int p_18951_) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(
                int p_18944_,
                ItemStack p_18945_
        ) {
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player p_18946_) {
            return false;
        }

        @Override
        public void clearContent() {

        }
    };

    private final Position interactPosition;
    private final Consumer<I> associate;
    private final float rankingBoost;
    private final Predicate<I> canAccept;

    public int size() {
        return container.size();
    }

    public I getItem(int index) {
        return container.getItem(index);
    }

    public void setItem(
            int index,
            I item
    ) {
        container.setItem(index, item);
    }

    public boolean isFull() {
        return container.isFull();
    }

    public BlockPos getBlockPos() {
        return Positions.ToBlock(getPosition(), getYPosition());
    }

    public String toShortString(boolean includeAir) {
        return container.toShortString(includeAir);
    }

    public String toShortString() {
        return this.toShortString(true);
    }

    public @Nullable Map.Entry<ContainerTarget<C, I>, I> withItemRemoved(Predicate<I> itemCheck) {
        for (int i = 0; i < container.size(); i++) {
            if (itemCheck.test(container.getItem(i))) {
                I itm = container.getItem(i);
                container.removeItem(i);
                return new AbstractMap.SimpleEntry<>(
                        new ContainerTarget<>(
                                position,
                                yPosition,
                                interactPosition,
                                container,
                                check,
                                associate,
                                canAccept,
                                rankingBoost
                        ),
                        itm.unit()
                );
            }
        }
        return null;
    }

    public float getRankingBoost() {
        return rankingBoost;
    }

    public boolean canAccept(I item) {
        return canAccept.test(item);
    }

    public interface Container<I extends Item<I>> extends ItemAccepting<I> {

        int size();

        I getItem(int i);

        I removeItem(
                int index
        );

        boolean isFull();

        String toShortString();

        String toShortString(boolean includeAir);

        boolean canAcceptIfSpaceAllows(I item);

        // This boost is factored into decision-making when villagers are
        // depositing items. It causes them to prefer this container over a
        // standard chest (or over another container with a lower rank-boost).
        RankBoost getItemAcceptanceRankBoost();
    }

    private final ValidCheck check;
    Position position;
    int yPosition;
    Container<I> container;

    public ImmutableList<I> getItems() {
        ImmutableList.Builder<I> b = ImmutableList.builder();
        for (int i = 0; i < container.size(); i++) {
            I item = container.getItem(i);
            associate.accept(item);
            b.add(item);
        }
        return b.build();
    }

    public interface ValidCheck {
        boolean IsStillValid();
    }

    public ContainerTarget(
            Position position,
            int yPosition,
            Position interactionPosition,
            @NotNull Container<I> container,
            ValidCheck check,
            Consumer<I> associate,
            @Nullable Predicate<I> canAccept,
            float rankingBoost
    ) {
        this.position = position;
        this.yPosition = yPosition;
        this.interactPosition = interactionPosition;
        this.container = container;
        this.check = check;
        this.associate = associate;
        this.canAccept = canAccept;
        this.rankingBoost = rankingBoost;
    }

    public Position getPosition() {
        return position;
    }

    public Position getInteractPosition() {
        return interactPosition;
    }

    public int getYPosition() {
        return yPosition;
    }

    public Container<I> getContainer() {
        return container;
    }

    public interface CheckFn<I extends Item> {
        boolean Matches(I item);
    }

    public boolean hasItem(CheckFn<I> c) {
        for (int i = 0; i < container.size(); i++) {
            if (c.Matches(container.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    public boolean isStillValid() {
        return check.IsStillValid();
    }

    @Override
    public String toString() {
        return "FoodTarget{" +
                "position=" + position +
                ", container=" + container +
                '}';
    }
}
