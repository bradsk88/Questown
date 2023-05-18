package ca.bradj.questown.gui;

import ca.bradj.questown.core.init.MenuTypesInit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.Collection;

public class VisitorQuestsContainer extends AbstractContainerMenu {

    public boolean isNewVisitor() {
        return ctx.isNewVisitor;
    }

    public static class VisitorContext {
        public final boolean isFirstVillager;
        public final boolean isNewVisitor;

        public VisitorContext(
                boolean isFirstVillager,
                boolean isNewVisitor
        ) {
            this.isFirstVillager = isFirstVillager;
            this.isNewVisitor = isNewVisitor;
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
        boolean isNewVisitor = data.readBoolean();
        return new VisitorContext(isFirstVillager, isNewVisitor);
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
}