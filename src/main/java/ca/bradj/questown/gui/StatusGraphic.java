package ca.bradj.questown.gui;

import ca.bradj.questown.core.Coordinate;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.OpenItemJobsMessage;
import ca.bradj.questown.core.network.OpenJobMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.IStatus;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.production.ProductionStatus;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static ca.bradj.questown.mc.Util.blit;

@SuppressWarnings("UnstableApiUsage")
public class StatusGraphic {

    private final EvictingQueue<IStatus<?>> statusSmoothingQueue = EvictingQueue.create(20);
    private final Supplier<Coordinate> topLeft;
    private final JobID jobId;
    private final Supplier<IStatus<?>> status;

    public StatusGraphic(
            JobID jobId,
            Supplier<Coordinate> topLeft,
            Supplier<IStatus<?>> status
    ) {
        this.topLeft = topLeft;
        this.jobId = jobId;
        this.status = status;
    }


    private @NotNull IStatus<?> getSmoothedStatus() {
        statusSmoothingQueue.add(status.get());
        HashMap<IStatus<?>, Integer> counter = new HashMap<>();
        for (IStatus<?> iStatus : statusSmoothingQueue) {
            counter.compute(iStatus, (ignored, oldCt) -> oldCt == null ? 1 : oldCt + 1);
        }
        return counter
                .entrySet()
                .stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElseThrow();
    }

    public void render(
            PoseStack stack
    ) {
        if (getSmoothedStatus() instanceof ProductionStatus ps) {
            RenderSystem.setShaderTexture(0, ClientAccess.getArt(jobId, ps));
        }
        int srcX = 0;
        int srcY = 0;
        Coordinate l = topLeft.get();
        int destX = l.x();
        int destY = l.y();
        int drawWidth = 32;
        int drawHeight = 32;
        int texWidth = 32;
        int texHeight = 32;
        blit(stack, destX, destY, srcX, srcY, drawWidth, drawHeight, texWidth, texHeight);
    }

    public boolean renderTooltip(
            int mouseX,
            int mouseY,
            Consumer<ImmutableList<Component>> renderTooltipKey
    ) {
        if (!isCoordOnGraphic(mouseX, mouseY)) {
            return false;
        }
        IStatus<?> status = getSmoothedStatus();
        ImmutableList<Component> components = JobTooltips.get((ProductionStatus) status, jobId);
        renderTooltipKey.accept(components);
        return true;
    }

    private boolean isCoordOnGraphic(
            int mouseX,
            int mouseY
    ) {
        Coordinate l = topLeft.get();
        Coordinate tl = new Coordinate(l.x(), l.y());
        boolean coordInBox = UtilClean.isCoordInBox(new Coordinate(mouseX, mouseY), tl, tl.shifted(32, 32));
        return coordInBox;
    }

    public boolean mouseClicked(
            int mouseX,
            int mouseY
    ) {
        return isCoordOnGraphic(mouseX, mouseY);
    }
}