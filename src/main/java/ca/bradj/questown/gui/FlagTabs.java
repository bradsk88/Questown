package ca.bradj.questown.gui;

import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;

public class FlagTabs extends Tabs implements SubUI {

    public FlagTabs(
            @Nullable Runnable questsScreenFn,
            @Nullable Runnable villagerScreenFn
    ) {
        super(ImmutableList.of(
                new Tab(
                        (rc, x, y) -> rc.itemRenderer()
                                        .renderAndDecorateItem(Items.BOOK.getDefaultInstance(), x + 10, y + 7),
                        setScreen(questsScreenFn),
                        "tooltips.quests",
                        questsScreenFn == null
                ),
                new Tab(
                        (rc, x, y) -> rc.itemRenderer()
                                        .renderAndDecorateItem(Items.PLAYER_HEAD.getDefaultInstance(), x + 10, y + 7),
                        setScreen(villagerScreenFn),
                        "tooltips.villagers",
                        villagerScreenFn == null
                )
        ));
    }

    private static Runnable setScreen(Runnable s) {
        if (s == null) {
            return () -> {
            };
        }
        return s;
    }

    public static Runnable makeOpenFn(
            BlockPos fp,
            String type
    ) {
        Runnable fn = () -> QuestownNetwork.CHANNEL.sendToServer(new OpenFlagMenuMessage(
                fp.getX(), fp.getY(), fp.getZ(),
                type
        ));
        return fn;
    }

    public static FlagTabs forMenu(FlagTabsEmbedding menu) {
        Collection<String> enabledTabs = menu.getEnabledTabs();

        Function<String, Runnable> factory = typ -> {
            if (enabledTabs.contains(typ)) {
                return makeOpenFn(menu.getFlagPos(), typ);
            }
            return null;
        };
        return new FlagTabs(
                factory.apply(OpenFlagMenuMessage.QUESTS),
                factory.apply(OpenFlagMenuMessage.VILLAGERS)
        );
    }
}
