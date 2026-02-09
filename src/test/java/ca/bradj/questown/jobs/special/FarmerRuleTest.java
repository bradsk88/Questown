package ca.bradj.questown.jobs.special;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.integration.jobs.BeforeExtractEvent;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
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

class FarmerRuleTest {

    private static final BlockPos WORK_SPOT = new BlockPos(10, 64, 10);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Simple ItemAcceptor that tracks items given and always returns the context.
     */
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

    private static BeforeExtractEvent<Boolean> makeEvent(
            TestWorldAccess world,
            TestItemAcceptor entity
    ) {
        return new BeforeExtractEvent<>(
                world,
                entity,
                WORK_SPOT,
                Items.WHEAT_SEEDS,
                () -> {}
        );
    }

    private static BeforeExtractEvent<Boolean> makeEvent(
            TestWorldAccess world,
            TestItemAcceptor entity,
            net.minecraft.world.item.Item lastInsertedItem
    ) {
        return new BeforeExtractEvent<>(
                world,
                entity,
                WORK_SPOT,
                lastInsertedItem,
                () -> {}
        );
    }

    // ========== HarvestCropSpecialRule ==========

    @Test
    void harvestCrop_shouldReturnDrops_whenCropIsFullAge() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(WORK_SPOT, "age", 7, 7)
                .withDrops(WORK_SPOT, List.of(new ItemStack(Items.WHEAT, 1)));

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        HarvestCropSpecialRule rule = new HarvestCropSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNotNull(result, "Should return context when crop is full age");
        Assertions.assertEquals(1, entity.givenItems.size(), "Should give 1 item");
        Assertions.assertEquals(0, world.getPropertyValue(WORK_SPOT, "age"), "Age should be reset to 0");
        Assertions.assertEquals(1, world.getSoundsPlayed().size(), "Should play crop break sound");
    }

    @Test
    void harvestCrop_shouldReturnNull_whenBlockHasNoAgeProperty() {
        TestWorldAccess world = new TestWorldAccess();

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        HarvestCropSpecialRule rule = new HarvestCropSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Should return null when block has no age property");
        Assertions.assertTrue(entity.givenItems.isEmpty(), "Should not give any items");
    }

    @Test
    void harvestCrop_shouldReturnNull_whenCropNotFullAge() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(WORK_SPOT, "age", 3, 7);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        HarvestCropSpecialRule rule = new HarvestCropSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Should return null when crop is not full age");
    }

    // ========== DestroyBushSpecialRule ==========

    @Test
    void destroyBush_shouldGiveDropsAndRemoveBlock() {
        TestWorldAccess world = new TestWorldAccess()
                .withDrops(WORK_SPOT, List.of(new ItemStack(Items.SWEET_BERRIES, 3)));

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        DestroyBushSpecialRule rule = new DestroyBushSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNotNull(result, "Should return context when bush has drops");
        Assertions.assertEquals(1, entity.givenItems.size(), "Should give items");
        Assertions.assertTrue(world.wasBlockRemoved(WORK_SPOT), "Block should be removed");
        Assertions.assertEquals(1, world.getSoundsPlayed().size(), "Should play grass break sound");
    }

    @Test
    void destroyBush_shouldReturnNull_whenNoDrops() {
        TestWorldAccess world = new TestWorldAccess()
                .withDrops(WORK_SPOT, Collections.emptyList());

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        DestroyBushSpecialRule rule = new DestroyBushSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Should return null when no drops");
        Assertions.assertFalse(world.wasBlockRemoved(WORK_SPOT), "Block should not be removed");
    }

    // ========== TillWorkspotSpecialRule ==========

    @Test
    void tillWorkspot_shouldApplyTransformation_whenBlockCanBeTransformed() {
        TestWorldAccess world = new TestWorldAccess()
                .withToolTransformResult(WORK_SPOT, true);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        TillWorkspotSpecialRule rule = new TillWorkspotSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNotNull(result, "Should return context when block can be transformed");
        Assertions.assertTrue(world.wasTransformApplied(WORK_SPOT), "Transformation should be applied");
    }

    @Test
    void tillWorkspot_shouldReturnNull_whenBlockCannotBeTransformed() {
        TestWorldAccess world = new TestWorldAccess()
                .withToolTransformResult(WORK_SPOT, false);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity);

        TillWorkspotSpecialRule rule = new TillWorkspotSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Should return null when block cannot be transformed");
        Assertions.assertFalse(world.wasTransformApplied(WORK_SPOT), "Transformation should not be applied");
    }

    // ========== UseLastInsertedItemOnBlockSpecialRule ==========

    @Test
    void useItemOnBlock_shouldCallUseItem_whenSuccessful() {
        TestWorldAccess world = new TestWorldAccess()
                .withUseItemResult(WORK_SPOT, true);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity, Items.WHEAT_SEEDS);

        UseLastInsertedItemOnBlockSpecialRule rule = new UseLastInsertedItemOnBlockSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Rule always returns null regardless of success");
        Assertions.assertTrue(world.wasItemUsedOnBlock(WORK_SPOT), "useItemOnBlock should have been called");
    }

    @Test
    void useItemOnBlock_shouldCallUseItem_whenFailed() {
        TestWorldAccess world = new TestWorldAccess()
                .withUseItemResult(WORK_SPOT, false);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity, Items.WHEAT_SEEDS);

        UseLastInsertedItemOnBlockSpecialRule rule = new UseLastInsertedItemOnBlockSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Rule always returns null regardless of failure");
        Assertions.assertTrue(world.wasItemUsedOnBlock(WORK_SPOT), "useItemOnBlock should have been called even on failure");
    }

    // ========== CompostAtWorkspotSpecialRule ==========

    @Test
    void compost_shouldExtractProduct_whenFull() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(WORK_SPOT, "level", 8, 8);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity, Items.WHEAT_SEEDS);

        CompostAtWorkspotSpecialRule rule = new CompostAtWorkspotSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNotNull(result, "Should return context when product extracted");
        Assertions.assertEquals(1, entity.givenItems.size(), "Should give compost product");
        Assertions.assertEquals(0, world.getPropertyValue(WORK_SPOT, "level"), "Level should be reset to 0");
        Assertions.assertEquals(1, world.getSoundsPlayed().size(), "Should play composter empty sound");
    }

    @Test
    void compost_shouldInsertItem_whenNotFull() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(WORK_SPOT, "level", 3, 8);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity, Items.WHEAT_SEEDS);

        CompostAtWorkspotSpecialRule rule = new CompostAtWorkspotSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNotNull(result, "Should return context when compost item inserted");
        Assertions.assertEquals(4, world.getPropertyValue(WORK_SPOT, "level"), "Level should be incremented");
    }

    @Test
    void compost_shouldSkipLevel7_whenAtLevel6() {
        TestWorldAccess world = new TestWorldAccess()
                .withBlockProperty(WORK_SPOT, "level", 6, 8);

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity, Items.WHEAT_SEEDS);

        CompostAtWorkspotSpecialRule rule = new CompostAtWorkspotSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNotNull(result, "Should return context when compost item inserted");
        Assertions.assertEquals(8, world.getPropertyValue(WORK_SPOT, "level"), "Level should skip 7 and jump to 8");
    }

    @Test
    void compost_shouldReturnNull_whenNoLevelProperty() {
        TestWorldAccess world = new TestWorldAccess();

        TestItemAcceptor entity = new TestItemAcceptor();
        BeforeExtractEvent<Boolean> event = makeEvent(world, entity, Items.WHEAT_SEEDS);

        CompostAtWorkspotSpecialRule rule = new CompostAtWorkspotSpecialRule();
        Boolean result = rule.beforeExtract(true, event);

        Assertions.assertNull(result, "Should return null when block has no level property");
    }
}
