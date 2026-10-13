package ca.bradj.questown.gui.town.status;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.OpenJobMessage;
import ca.bradj.questown.core.network.OpenVillagerMenuMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.gui.*;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static ca.bradj.questown.gui.PagedCardScreen.*;

public class MultiStatusScreen extends AbstractPagedCardScreen<MultiStatusMenu, UUID> {

    private static final UUID MORNING_ARRIVAL_SENTINEL = new UUID(0, 0);

    private final Map<UUID, Collection<StatusPacket>> statusSmoothingQueue = new HashMap<>();
    private final FlagTabs tabs;

    public record SyncedData(Map<UUID, StatusPacket> villagerStatuses,
                             Map<UUID, ImmutableList<net.minecraft.world.item.Item>> items,
                             boolean villagerArrivingInMorning) {
        public JobID getJob(UUID uuid) {
            return Util.orNull(villagerStatuses.get(uuid), StatusPacket::jobId);
        }
    }

    static public SyncedData syncedData = new SyncedData(new HashMap<>(), new HashMap<>(), false);

    public MultiStatusScreen(
            MultiStatusMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(
                menu, new Inventory(null) {
                    @Override
                    public Component getDisplayName() {
                        return Compat.literal("");
                    }
                }, Compat.literal("")
        );
        this.tabs = FlagTabs.forMenu(menu, "tooltips.villagers");
    }

    @Override
    protected void renderScreenExtras(
            RenderContext ctx,
            int mouseX,
            int mouseY,
            float partialTicks
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - super.backgroundHeight()) / 2;
        tabs.draw(ctx, bgX, bgY);
    }

    @Override
    protected ImmutableList<UUID> cardsData() {
        ImmutableList<UUID> villagers = ImmutableList.copyOf(syncedData.villagerStatuses.keySet());
        if (!syncedData.villagerArrivingInMorning()) {
            return villagers;
        }
        return ImmutableList.<UUID>builder().addAll(villagers).add(MORNING_ARRIVAL_SENTINEL).build();
    }

    @Override
    protected List<Component> renderCardContent(
            RenderContext ctx,
            Card<UUID> card,
            int mouseX,
            int mouseY
    ) {
        if (MORNING_ARRIVAL_SENTINEL.equals(card.data())) {
            // With no townies yet the "...and one more" continuation has nothing to continue
            // from - the day-1 playtest flagged it as copy that only works after the first
            // arrival. Name the arrival directly instead.
            String key = syncedData.villagerStatuses.isEmpty()
                    ? "gui.status.villager_arriving_morning.first"
                    : "gui.status.villager_arriving_morning";
            CardCoordinates cc = card.coords();
            // Wrap to the card's padded inner width - a single un-wrapped ctx.text ran
            // "A new villager arrives in the morning" off the card's right edge.
            ctx.textWrap(new Coordinate(cc.leftXPadded(), cc.topYPadded()),
                    cc.rightXPadded() - cc.leftXPadded(), Compat.translatable(key), 0xFF808080);
            return null;
        }
        renderStatus(ctx, card.coords(), card.data());
        renderInventory(ctx, card.coords(), card.data());
        renderFace(ctx, card.coords(), card.data());
        return null;
    }

    private void renderInventory(
            RenderContext ctx,
            CardCoordinates coords,
            UUID uuid
    ) {
        int iconX = coords.leftXPadded();
        Collection<net.minecraft.world.item.Item> items = syncedData.items.get(uuid);
        for (Item item : items) {
            int destX = iconX + SMALL_PADDING;
            int destY = coords.topYPadded() + BIG_PADDING;
            Compat.renderItemAndDecorations(ctx.gui(), new ItemStack(item), destX, destY);
            iconX += 16;
        }
    }

    private void renderFace(
            RenderContext ctx,
            CardCoordinates coords,
            UUID uuid
    ) {
        int x = coords.leftXPadded();
        int destY = coords.topYPadded();
        Util.blitFace(ctx.gui(), uuid, x, destY);
        int nameX = x + Util.faceWidth + 4;
        ctx.darkText(Compat.translatable(syncedData.villagerStatuses.get(uuid).jobId().rootId()), nameX, destY);
    }

    private void renderStatus(
            RenderContext ctx,
            PagedCardScreen.CardCoordinates coords,
            UUID uuid
    ) {
        int drawWidth = 32;
        int drawHeight = 32;
        int texWidth = 32;
        int texHeight = 32;

        int destX = getStatusX(coords);
        int destY = getStatusY(coords);
        @NotNull StatusPacket status = getSmoothedStatus(uuid);
        // 32x32 status art: pass the draw size as the sheet (the 8-arg Util.blit normalises
        // against the 256 sheet and would sample only the leftmost 4 px - the "flat grey box" bug).
        Util.blit(ctx.gui(), status.image(), destX, destY, 0, 0, drawWidth, drawHeight, drawWidth, drawHeight);
        renderLeaverReturnBar(ctx, status, destX, destY);
    }

    /**
     * G3: progress bar for a leaver away on a timed trip. The payload carries
     * the absolute server return tick; the fraction is re-derived every frame
     * from the client's own clock (container screens re-render continuously),
     * so the bar ticks down live while the menu is open and fills as the
     * leaver's timer expires - the re-sync on status change then drops it.
     */
    private void renderLeaverReturnBar(
            RenderContext ctx,
            StatusPacket status,
            int x,
            int y
    ) {
        if (status.leaverReturnTick() <= 0 || status.leaverReturnTotal() <= 0) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long remaining = status.leaverReturnTick() - level.getGameTime();
        double fraction = Math.min(1.0, Math.max(0.0,
                1.0 - remaining / (double) status.leaverReturnTotal()));
        int barWidth = 30;
        int barHeight = 3;
        int bx = x + 1;
        int by = y + 32 - 1 - barHeight;
        ctx.fill(bx, by, bx + barWidth, by + barHeight, 0x80202020);
        int filled = (int) Math.round(fraction * barWidth);
        if (filled > 0) {
            ctx.fill(bx, by, bx + filled, by + barHeight, 0xFF40C040);
        }
    }

    private static int getStatusY(CardCoordinates coords) {
        return coords.topYPadded() - MED_PADDING;
    }

    private static int getStatusX(
            CardCoordinates coords
    ) {
        return coords.rightXPadded() - 32;
    }

    private @NotNull StatusPacket getSmoothedStatus(UUID villagerUUID) {
        Collection<StatusPacket> q = UtilClean.getOrDefaultCollection(
                statusSmoothingQueue,
                villagerUUID,
                EvictingQueue.create(5),
                true
        );
        q.add(syncedData.villagerStatuses.get(villagerUUID));
        statusSmoothingQueue.put(villagerUUID, q);
        HashMap<StatusPacket, Integer> counter = new HashMap<>();
        for (StatusPacket iStatus : q) {
            counter.compute(iStatus, (ignored, oldCt) -> oldCt == null ? 1 : oldCt + 1);
        }
        return counter.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue)).map(Map.Entry::getKey)
                      .orElseThrow();
    }

    @Override
    protected void renderTooltipContent(
            RenderContext ctx,
            int mouseX,
            int mouseY
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight()) / 2;

        if (this.tabs.renderTooltip(
                bgX,
                bgY,
                mouseX,
                mouseY,
                key -> this.showTooltip(Compat.translatable(key))
        )) {
            return;
        }

        for (Card<UUID> v : cards()) {
            if (MORNING_ARRIVAL_SENTINEL.equals(v.data())) continue;
            CardCoordinates coords = v.coords();
            int x = getStatusX(coords);
            int y = getStatusY(v.coords());
            if (UtilClean.isCoordInBox(mouseX, mouseY, x, y, 32, 32)) {
                @NotNull StatusPacket status = getSmoothedStatus(v.data());
                this.showTooltip(status.texts().stream().map(Compat::componentText).toList());
                return;
            }
        }
        super.renderTooltipContent(ctx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(
            double p_97748_,
            double p_97749_,
            int p_97750_
    ) {
        int bgX = (this.width - backgroundWidth) / 2;
        int bgY = (this.height - backgroundHeight()) / 2;
        tabs.mouseClicked(bgX, bgY, p_97748_, p_97749_);

        for (Card<UUID> v : cards()) {
            if (MORNING_ARRIVAL_SENTINEL.equals(v.data())) continue;
            CardCoordinates coords = v.coords();
            int x = getStatusX(coords);
            int y = getStatusY(v.coords());
            if (UtilClean.isCoordInBox(p_97748_, p_97749_, x, y, 32, 32)) {
                QuestownNetwork.sendToServer(new OpenJobMessage(
                        menu.getFlagInfo().flagPos(), syncedData.getJob(v.data())
                ));
                return true;
            }
        };

        // Full-card click opens that villager's UI in management-only mode (same whole-card
        // hit-test the quest tab uses in openLessonForClickedCard; user reference, 2026-10-14
        // playtest): the menu hides the inventory tab and lands on the stats pane, not the
        // inventory pane.
        for (Card<UUID> v : cards()) {
            if (MORNING_ARRIVAL_SENTINEL.equals(v.data())) continue;
            CardCoordinates c = v.coords();
            if (p_97748_ < c.leftX() || p_97748_ > c.rightX() || p_97749_ < c.topY() || p_97749_ > c.bottomY()) {
                continue;
            }
            BlockPos flagPos = menu.getFlagInfo().flagPos();
            QuestownNetwork.sendToServer(new OpenVillagerMenuMessage(
                    flagPos.getX(), flagPos.getY(), flagPos.getZ(),
                    v.data(), OpenVillagerMenuMessage.STATS, false
            ));
            return true;
        }

        return super.mouseClicked(p_97748_, p_97749_, p_97750_);
    }

    @Override
    protected void setRenderColorForCard(UUID uuid) {
    }
}