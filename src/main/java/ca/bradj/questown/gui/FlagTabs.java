package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.core.network.OpenFlagMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Function;

public class FlagTabs extends Tabs implements SubUI {

    public FlagTabs(
            @Nullable Runnable questsScreenFn,
            @Nullable Runnable villagerScreenFn,
            @Nullable Runnable econScreenFn,
            @Nullable Runnable bopScreenFn,
            boolean showBOPTab
    ) {
        super(build(questsScreenFn, villagerScreenFn, econScreenFn, bopScreenFn, showBOPTab));
    }

    private static @NotNull ImmutableList<Tab> build(
            @Nullable Runnable questsScreenFn,
            @Nullable Runnable villagerScreenFn,
            @Nullable Runnable econScreenFn,
            @Nullable Runnable bopScreenFn,
            boolean showBOPTab
    ) {
        ImmutableList.Builder<Tab> b = ImmutableList.builder();
        b.add(new Tab(
                (rc, x, y) -> rc.itemRenderer()
                                .renderAndDecorateItem(Items.PLAYER_HEAD.getDefaultInstance(), x + 10, y + 7),
                setScreen(villagerScreenFn),
                "tooltips.villagers",
                villagerScreenFn == null
        ));
        b.add(new Tab(
                (rc, x, y) -> rc.itemRenderer()
                                .renderAndDecorateItem(Items.BOOK.getDefaultInstance(), x + 10, y + 7),
                setScreen(questsScreenFn),
                "tooltips.quests",
                questsScreenFn == null
        ));
        b.add(new Tab(
                (rc, x, y) -> {
                    int txBefore = RenderSystem.getShaderTexture(0);
                    Util.blitTab(rc.stack(), x, y, 0);
                    RenderSystem.setShaderTexture(0, txBefore);
                },
                setScreen(econScreenFn),
                "tooltips.economics",
                econScreenFn == null
        ));
        if (showBOPTab) {
            b.add(new Tab(
                    (rc, x, y) -> RenderUtil.renderItemScaled(
                            rc.itemRenderer(),
                            2,
                            ItemsInit.BLOCK_OF_PROGRESS.get().getDefaultInstance(),
                            x + 10,
                            y + 7
                    ),
                    setScreen(bopScreenFn),
                    "tooltips.blocks_of_progress",
                    bopScreenFn == null
            ));
        }
        return b.build();
    }

    private static Runnable setScreen(Runnable s) {
        if (s == null) {
            return () -> {
            };
        }
        return s;
    }

    public static Runnable makeOpenFn(
            FlagTabsEmbedding.FlagInfo fp,
            String type
    ) {
        Runnable fn = () -> QuestownNetwork.CHANNEL.sendToServer(new OpenFlagMenuMessage(fp, type));
        return fn;
    }

    public static FlagTabs forMenu(FlagTabsEmbedding menu) {
        Collection<String> enabledTabs = menu.getEnabledTabs();

        Function<String, Runnable> factory = typ -> {
            if (enabledTabs.contains(typ)) {
                return makeOpenFn(menu.getFlagInfo(), typ);
            }
            return null;
        };
        return new FlagTabs(
                factory.apply(OpenFlagMenuMessage.QUESTS),
                factory.apply(OpenFlagMenuMessage.VILLAGERS),
                factory.apply(OpenFlagMenuMessage.ECONOMICS),
                factory.apply(OpenFlagMenuMessage.BOP),
                menu.getFlagInfo().showBlockOfProgressTab()
        );
    }
}
