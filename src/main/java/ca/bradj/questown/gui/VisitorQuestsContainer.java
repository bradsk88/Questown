package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;

public class VisitorQuestsContainer extends AbstractContainerMenu {

    public boolean isNewVisitor() {
        return ctx.finishedQuests == 0 && ctx.unfinishedQuests > 0;
    }

    public int finishedQuests() {
        return ctx.finishedQuests;
    }

    public int unfinishedQuests() {
        return ctx.unfinishedQuests;
    }

    public static class VisitorContext {
        public final boolean isFirstVillager;
        public final int finishedQuests;
        public final int unfinishedQuests;

        public VisitorContext(
                boolean isFirstVillager,
                int finishedQuests,
                int unfinishedQuests
        ) {
            this.isFirstVillager = isFirstVillager;
            this.finishedQuests = finishedQuests;
            this.unfinishedQuests = unfinishedQuests;
        }
    }

    private final Collection<UIQuest> quests;
    private VisitorContext ctx;

    public VisitorQuestsContainer(
            int windowId,
            Collection<UIQuest> quests,
            VisitorContext ctx
    ) {
        super(MenuTypesInit.VISITOR_QUESTS.get(), windowId);
        this.quests = quests;
        this.ctx = ctx;
    }

    public VisitorQuestsContainer(
            int windowId,
            Inventory inv,
            FriendlyByteBuf data
    ) {
        this(windowId, readQuests(data), readVisitor(data));
    }

    private static Collection<UIQuest> readQuests(FriendlyByteBuf data) {
        int size = data.readInt();
        ArrayList<UIQuest> r = data.readCollection(c -> new ArrayList<>(size), buf -> {
            ResourceLocation recipeID = buf.readResourceLocation();
            return new UIQuest.Serializer().fromNetwork(recipeID, buf);
        });
        r.sort(UIQuest::compareTo);
        return r;
    }

    private static VisitorContext readVisitor(FriendlyByteBuf data) {
        boolean isFirstVillager = data.readBoolean();
        int finishedQuests = data.readInt();
        int unfinishedQuests = data.readInt();
        return new VisitorContext(
                isFirstVillager, finishedQuests, unfinishedQuests
        );
    }

    @Override
    public boolean stillValid(Player p_38874_) {
        return true;
    }

    public Collection<UIQuest> GetQuests() {
        return this.quests;
    }

    public boolean isFirstVisitor() {
        return this.ctx.isFirstVillager;
    }

    @Override
    public ItemStack quickMoveStack(
            Player p_38941_,
            int p_38942_
    ) {
        return ItemStack.EMPTY;
    }
}