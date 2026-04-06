package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.InsertIntoSlotSpecialRule;
import ca.bradj.questown.integration.TakeFromSlotSpecialRule;
import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobBlockTestContext;
import ca.bradj.questown.jobs.WorkedSpot;
import ca.bradj.questown.mobs.visitor.ItemAcceptor;
import ca.bradj.questown.world.TestWorldAccess;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

class ContainerRuleTest {

    private static final BlockPos WORK_SPOT = new BlockPos(10, 64, 10);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static class TestItemAcceptor implements ItemAcceptor<Boolean> {
        final List<MCHeldItem> givenItems = new ArrayList<>();

        @Override
        public @Nullable Boolean tryGiveItem(
                Boolean town,
                MCHeldItem item,
                InventoryFullStrategy inventoryFullStrategy
        ) {
            givenItems.add(item);
            return town;
        }
    }

    // ========== InsertIntoSlotSpecialRule ==========

    @Test
    void insertIntoSlot_shouldPlaceItemInCorrectSlot() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 3);

        ItemStack coal = new ItemStack(Items.COAL, 1);
        AfterInsertItemEvent<Boolean> event = new AfterInsertItemEvent<>(
                world,
                coal,
                new WorkedSpot<>(WORK_SPOT, 0),
                town -> town,
                java.util.UUID.randomUUID()
        );

        InsertIntoSlotSpecialRule rule = new InsertIntoSlotSpecialRule(1);
        rule.afterInsertItem(true, event);

        Assertions.assertTrue(
                ItemStack.matches(coal, world.getSlotContents(WORK_SPOT, 1)),
                "Item should be in slot 1"
        );
        Assertions.assertTrue(
                world.getSlotContents(WORK_SPOT, 0).isEmpty(),
                "Slot 0 should remain empty"
        );
    }

    @Test
    void insertIntoSlot_shouldLogError_whenNotAContainer() {
        TestWorldAccess world = new TestWorldAccess(); // no container

        ItemStack coal = new ItemStack(Items.COAL, 1);
        AfterInsertItemEvent<Boolean> event = new AfterInsertItemEvent<>(
                world,
                coal,
                new WorkedSpot<>(WORK_SPOT, 0),
                town -> town,
                java.util.UUID.randomUUID()
        );

        InsertIntoSlotSpecialRule rule = new InsertIntoSlotSpecialRule(0);
        // Should not throw — just logs an error
        rule.afterInsertItem(true, event);
    }

    // ========== TakeFromSlotSpecialRule ==========

    @Test
    void takeFromSlot_shouldExtractItemAndGiveToEntity() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 3)
                .withContainerSlot(WORK_SPOT, 1, new ItemStack(Items.IRON_INGOT, 5));

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = new BeforeExtractEvent<>(
                world,
                entity,
                WORK_SPOT,
                Items.IRON_INGOT,
                () -> {},
                () -> Collections.singletonList(WORK_SPOT)
        );

        TakeFromSlotSpecialRule rule = new TakeFromSlotSpecialRule(1);
        rule.beforeExtract(true, event);

        Assertions.assertEquals(1, entity.givenItems.size(), "Should give 1 item to entity");
        Assertions.assertEquals(4, world.getSlotContents(WORK_SPOT, 1).getCount(),
                "Slot should have 4 remaining after extracting 1");
    }

    @Test
    void takeFromSlot_shouldReturnContext_whenSlotEmpty() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 3);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = new BeforeExtractEvent<>(
                world,
                entity,
                WORK_SPOT,
                Items.IRON_INGOT,
                () -> {},
                () -> Collections.singletonList(WORK_SPOT)
        );

        TakeFromSlotSpecialRule rule = new TakeFromSlotSpecialRule(1);
        rule.beforeExtract(true, event);

        Assertions.assertTrue(entity.givenItems.isEmpty(), "Should not give any items when slot is empty");
    }

    // ========== AddItemToContainerSpecialRule ==========

    @Test
    void addItemToContainer_shouldInsertIntoFirstOpenSlot() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 3)
                .withContainerSlot(WORK_SPOT, 0, new ItemStack(Items.DIAMOND, 1));

        ItemStack wheat = new ItemStack(Items.WHEAT, 1);
        AfterInsertItemEvent<Boolean> event = new AfterInsertItemEvent<>(
                world,
                wheat,
                new WorkedSpot<>(WORK_SPOT, 0),
                town -> town,
                java.util.UUID.randomUUID()
        );

        AddItemToContainerSpecialRule rule = new AddItemToContainerSpecialRule();
        rule.afterInsertItem(true, event);

        Assertions.assertTrue(
                world.getSlotContents(WORK_SPOT, 1).sameItem(wheat),
                "Item should be in first open slot (slot 1)"
        );
    }

    @Test
    void addItemToContainer_shouldReturnContext_whenContainerFull() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 2)
                .withContainerSlot(WORK_SPOT, 0, new ItemStack(Items.DIAMOND, 1))
                .withContainerSlot(WORK_SPOT, 1, new ItemStack(Items.COAL, 1));

        ItemStack wheat = new ItemStack(Items.WHEAT, 1);
        AfterInsertItemEvent<Boolean> event = new AfterInsertItemEvent<>(
                world,
                wheat,
                new WorkedSpot<>(WORK_SPOT, 0),
                town -> town,
                java.util.UUID.randomUUID()
        );

        AddItemToContainerSpecialRule rule = new AddItemToContainerSpecialRule();
        // Should not throw — just logs an error about lost item
        rule.afterInsertItem(true, event);
    }

    // ========== RequireTwoFreeSpotsSpecialRule ==========

    @Test
    void requireTwoFreeSpots_shouldPassJobBlockCheck_whenTwoSlotsEmpty() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 4)
                .withContainerSlot(WORK_SPOT, 0, new ItemStack(Items.DIAMOND, 1))
                .withContainerSlot(WORK_SPOT, 1, new ItemStack(Items.COAL, 1));

        Predicate<JobBlockTestContext> captured = runJobBlockCheck(world);

        JobBlockTestContext ctx = new JobBlockTestContext(
                world, null, WORK_SPOT,
                Collections::emptyList, Collections::emptyList,
                false, false
        );

        Assertions.assertTrue(captured.test(ctx), "Should pass when 2 slots are empty");
    }

    @Test
    void requireTwoFreeSpots_shouldFailJobBlockCheck_whenOnlyOneSlotEmpty() {
        TestWorldAccess world = new TestWorldAccess()
                .withContainer(WORK_SPOT, 3)
                .withContainerSlot(WORK_SPOT, 0, new ItemStack(Items.DIAMOND, 1))
                .withContainerSlot(WORK_SPOT, 1, new ItemStack(Items.COAL, 1));

        Predicate<JobBlockTestContext> captured = runJobBlockCheck(world);

        JobBlockTestContext ctx = new JobBlockTestContext(
                world, null, WORK_SPOT,
                Collections::emptyList, Collections::emptyList,
                false, false
        );

        Assertions.assertFalse(captured.test(ctx), "Should fail when only 1 slot is empty");
    }

    @Test
    void requireTwoFreeSpots_shouldFailJobBlockCheck_whenNotAContainer() {
        TestWorldAccess world = new TestWorldAccess();

        Predicate<JobBlockTestContext> captured = runJobBlockCheck(world);

        JobBlockTestContext ctx = new JobBlockTestContext(
                world, null, WORK_SPOT,
                Collections::emptyList, Collections::emptyList,
                false, false
        );

        Assertions.assertFalse(captured.test(ctx), "Should fail when not a container");
    }

    private static Predicate<JobBlockTestContext> runJobBlockCheck(TestWorldAccess world) {
        JobCheckReplacer jobCheckReplacer = new JobCheckReplacer(ctx -> true);
        SupplyRoomCheckReplacer supplyRoomCheckReplacer = new SupplyRoomCheckReplacer();

        BeforeInitEvent event = new BeforeInitEvent(
                () -> world,
                ItemCheckReplacer.doNotReplace(),
                ItemCheckReplacer.doNotReplace(),
                jobCheckReplacer,
                supplyRoomCheckReplacer
        );

        RequireTwoFreeSpotsSpecialRule rule = new RequireTwoFreeSpotsSpecialRule();
        rule.beforeInit(event);

        return ctx -> JobCheckReplacer.withContext(jobCheckReplacer, ctx).test(ctx.blockPos());
    }
}
