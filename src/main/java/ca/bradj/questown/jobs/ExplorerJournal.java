package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.gui.SessionUniqueOrdinals;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;

public class ExplorerJournal<I extends Item<I>, H extends HeldItem<H, I> & Item<H>> extends GathererJournal<I, H> {

    public ExplorerJournal(
            SignalSource sigs,
            EmptyFactory<H> ef,
            Converter<I, H> converter,
            GathererStatuses.TownStateProvider cont,
            int inventoryCapacity
    ) {
        super(sigs, ef, converter, cont, inventoryCapacity, (a) -> new Tools(false, false, false, false));
    }

    @Override
    public Snapshot<H> getSnapshot(EmptyFactory<H> ef) {
        return new Snapshot<>(ExplorerJob.ID, getStatus(), getItems());
    }
}