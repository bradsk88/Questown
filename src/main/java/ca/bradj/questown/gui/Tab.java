package ca.bradj.questown.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.util.TriConsumer;

import java.util.function.Consumer;

public record Tab(
        TriConsumer<PoseStack, Integer, Integer> renderFunc,
        Consumer<Minecraft> onClick,
        String titleKey,
        boolean selected
) {
}