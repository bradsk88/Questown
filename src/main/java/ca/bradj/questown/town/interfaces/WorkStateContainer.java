package ca.bradj.questown.town.interfaces;

import ca.bradj.questown.town.AbstractWorkStatusStore;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

public interface WorkStateContainer<POS> extends ImmutableWorkStateContainer<POS, Void> {

    @Nullable AbstractWorkStatusStore.State getJobBlockState(POS bp);

    ImmutableMap<POS, AbstractWorkStatusStore.State> getAll();
}
