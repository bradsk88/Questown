package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.jobs.GathererJournal;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.List;

public class ContainerTarget<C extends ContainerTarget.Container<I>, I extends GathererJournal.Item<I>> {

    public int size() {
        return container.size();
    }

    public I getItem(int index) {
        return container.getItem(index);
    }

    public void setItem(int index, I item) {
        container.setItem(index, item);
    }

    public boolean isFull() {
        return container.isFull();
    }

    public interface Container<I extends GathererJournal.Item<I>> {

        int size();

        I getItem(int i);

        boolean hasAnyOf(ImmutableSet<I> items);

        void setItems(List<I> newItems);

        void removeItem(
                int index,
                int amount
        );

        void setItem(
                int i,
                I item
        );

        boolean isFull();
    }

    private final ValidCheck check;
    Position position;
    int yPosition;
    Container<I> container;

    public ImmutableList<I> getItems() {
        ImmutableList.Builder<I> b = ImmutableList.builder();
        for (int i = 0; i < container.size(); i++) {
            b.add(container.getItem(i));
        }
        return b.build();
    }

    public void setItems(List<I> newItems) {
        container.setItems(newItems);
    }

    public interface ValidCheck {
        boolean IsStillValid();
    }

    public ContainerTarget(
            Position position,
            int yPosition,
            Container<I> container,
            ValidCheck check
    ) {
        this.position = position;
        this.yPosition = yPosition;
        this.container = container;
        this.check = check;
    }

    public Position getPosition() {
        return position;
    }

    public int getyPosition() {
        return yPosition;
    }

    public boolean hasAnyOf(ImmutableSet<I> items) {
        return container.hasAnyOf(items);
    }

    public interface CheckFn<I extends GathererJournal.Item> {
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
