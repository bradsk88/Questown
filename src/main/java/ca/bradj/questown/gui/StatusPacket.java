package ca.bradj.questown.gui;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

record StatusPacket(
        JobID jobId,
        ImmutableList<Component> texts,
        ResourceLocation image
) {
}
