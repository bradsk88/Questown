package ca.bradj.questown.gui;

import ca.bradj.questown.town.quests.Quest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the item-quest deposit progress (the "(3/8)" on the "Create N items"
 * card, 2026-10-14 playtest 04:26:16): the {@code deposited} field must
 * survive the UIQuest network round-trip (-1 == null) and must be appended
 * to the card name, capped at the target.
 *
 * Registry note: constructing Ingredients / resolving the item name needs
 * the vanilla bootstrap (SharedConstants + Bootstrap.bootStrap) before any
 * registry-bound class loads; the serializer itself is pure byte-buffer
 * math.
 */
public class UIQuestItemProgressTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static UIQuest itemQuest(Integer deposited) {
        return new UIQuest(
                UUID.randomUUID(),
                new ResourceLocation("minecraft:mutton"),
                Quest.QuestType.ITEM,
                Collections.nCopies(8, Ingredient.of(Items.MUTTON)),
                Quest.QuestStatus.ACTIVE,
                null,
                null,
                null,
                null,
                deposited
        );
    }

    @Test
    public void deposited_survives_wire_round_trip() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(1024));
        UIQuest.Serializer serializer = new UIQuest.Serializer();
        serializer.toNetwork(buf, itemQuest(3));
        buf.readerIndex(0);
        UIQuest back = serializer.fromNetwork(new ResourceLocation("probe"), buf);
        assertEquals(3, back.getDeposited());
        assertEquals(Quest.QuestType.ITEM, back.getType());
    }

    @Test
    public void null_deposited_serializes_as_minus_one() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(1024));
        UIQuest.Serializer serializer = new UIQuest.Serializer();
        serializer.toNetwork(buf, itemQuest(null));
        buf.readerIndex(0);
        UIQuest back = serializer.fromNetwork(new ResourceLocation("probe"), buf);
        assertNull(back.getDeposited());
    }

    @Test
    public void card_name_appends_progress_capped_at_target() {
        // The progress suffix carries a leading space (" (n/m)") - the 2026-10-15
        // playtest fix; the assertions here pin that shape.
        assertEquals("menu.common.quantity (3/8)", itemQuest(3).getName().getString());
        assertEquals("menu.common.quantity (8/8)", itemQuest(8).getName().getString());
        // 12 deposited but the quest wants 8 - never show "(12/8)".
        assertEquals("menu.common.quantity (8/8)", itemQuest(12).getName().getString());
        // No progress data (non-item quest or no scan) - plain name.
        assertEquals("menu.common.quantity", itemQuest(null).getName().getString());
    }
}

