package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.BlocksInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.mobs.helperchicken.ChickenArcBubbles.Bubble;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Single source of truth mapping {@code (ChickenBeatState, BeatPhase)} to a
 * {@link Presentation} (bubble icon, hint lang key, plain lang key).
 *
 * <p>Bubble + hint + plain all read from the same row, so drift between them
 * is impossible by construction. {@link #activePhase} decides the phase once
 * from {@link PhaseInputs}; {@link #present} looks up the row.
 *
 * <p>See {@code /CONTEXT.md} for vocabulary and
 * {@code docs/adr/0001-chicken-arc-beat-phase.md} for the design decision.
 */
public final class ChickenArcPresentation {

    private static final String HINT_STICK = "message.questown.chicken.hint.stick";
    private static final String HINT_STICK_USE = "message.questown.chicken.hint.stick.use_on_flag";
    private static final String HINT_WAND_CAMPFIRE = "message.questown.chicken.hint.wand_on_campfire";
    private static final String HINT_WAND_CAMPFIRE_USE = "message.questown.chicken.hint.wand_on_campfire.use";
    private static final String HINT_SUNSET = "message.questown.chicken.hint.sunset";
    private static final String HINT_SUNSET_PREPARING = "message.questown.chicken.hint.sunset.preparing";
    private static final String HINT_WALL_BLOCK = "message.questown.chicken.hint.wall_block";
    private static final String HINT_WALL_BLOCK_WITH_ITEM = "message.questown.chicken.hint.wall_block.with_item";
    private static final String HINT_DOOR = "message.questown.chicken.hint.door";
    private static final String HINT_DOOR_WITH_ITEM = "message.questown.chicken.hint.door.with_item";
    private static final String HINT_WAND_DOOR = "message.questown.chicken.hint.wand_on_door";
    private static final String HINT_WAND_DOOR_USE = "message.questown.chicken.hint.wand_on_door.use";
    private static final String HINT_SIGN = "message.questown.chicken.hint.sign";
    private static final String HINT_CHEST = "message.questown.chicken.hint.chest";
    private static final String HINT_CHEST_WITH_ITEM = "message.questown.chicken.hint.chest.with_item";
    private static final String HINT_PRESSURE_PLATE = "message.questown.chicken.hint.pressure_plate";
    private static final String HINT_PRESSURE_PLATE_USE = "message.questown.chicken.hint.pressure_plate.use";
    private static final String HINT_WELCOME_MAT = "message.questown.chicken.hint.welcome_mat";
    private static final String HINT_VILLAGER_UI = "message.questown.chicken.hint.villager_ui";
    private static final String HINT_FLAG_UI = "message.questown.chicken.hint.flag_ui";
    private static final String HINT_WORLDLY_SEEDS = "message.questown.chicken.hint.worldly_seeds";

    private static final String PLAIN_STICK = "message.questown.chicken.plain.stick";
    private static final String PLAIN_WAND_CAMPFIRE = "message.questown.chicken.plain.wand_on_campfire";
    private static final String PLAIN_SUNSET = "message.questown.chicken.plain.sunset";
    private static final String PLAIN_SUNSET_PREPARING = "message.questown.chicken.plain.sunset_preparing";
    private static final String PLAIN_WALL_BLOCK = "message.questown.chicken.plain.wall_block";
    private static final String PLAIN_DOOR = "message.questown.chicken.plain.door";
    private static final String PLAIN_WAND_DOOR = "message.questown.chicken.plain.wand_on_door";
    private static final String PLAIN_SIGN = "message.questown.chicken.plain.sign";
    private static final String PLAIN_CHEST = "message.questown.chicken.plain.chest";
    private static final String PLAIN_PRESSURE_PLATE = "message.questown.chicken.plain.pressure_plate";
    private static final String PLAIN_WELCOME_MAT = "message.questown.chicken.plain.welcome_mat";
    private static final String PLAIN_VILLAGER_UI = "message.questown.chicken.plain.villager_ui";
    private static final String PLAIN_FLAG_UI = "message.questown.chicken.plain.flag_ui";
    private static final String PLAIN_WORLDLY_SEEDS = "message.questown.chicken.plain.worldly_seeds";

    private ChickenArcPresentation() {
    }

    public static BeatPhase activePhase(ChickenBeatState state, PhaseInputs in) {
        return switch (state) {
            case WAITING_FOR_STICK,
                 WAITING_FOR_WAND_ON_CAMPFIRE,
                 WAITING_FOR_WAND_ON_DOOR -> in.hasItem() ? BeatPhase.READY_TO_USE : BeatPhase.NEED_TO_FETCH;
            case WAITING_FOR_WALL_BLOCK,
                 WAITING_FOR_DOOR -> in.hasItem() ? BeatPhase.READY_TO_PLACE : BeatPhase.NEED_TO_FETCH;
            case SUNSET_AND_MAP -> sunsetAndMapPhase(in);
            case WAITING_FOR_CHEST -> in.hasItem() ? BeatPhase.READY_TO_PLACE : BeatPhase.DEFAULT;
            case WAITING_FOR_PRESSURE_PLATE -> pressurePlatePhase(in);
            case WAITING_FOR_SIGN,
                 WAITING_FOR_VILLAGER_UI,
                 WAITING_FOR_FLAG_UI,
                 AWAITING_WORLDLY_SEEDS_DELIVERY,
                 COMPLETE,
                 FORFEIT -> BeatPhase.DEFAULT;
        };
    }

    private static BeatPhase sunsetAndMapPhase(PhaseInputs in) {
        if (!in.chestSpawned()) {
            return BeatPhase.PREPARING;
        }
        if (in.isNight()) {
            return BeatPhase.READY_TO_USE;
        }
        return BeatPhase.AWAITING_NIGHT;
    }

    /**
     * Three-phase pressure-plate beat (the welcome-mat craft flow). The phase
     * is decided by which item the player holds, in priority order:
     * <ol>
     *   <li>a welcome mat already in hand ({@code hasItem}) — place it at the
     *       gate, {@link BeatPhase#READY_TO_PLACE};</li>
     *   <li>a pressure plate in hand ({@code hasPressurePlate}) — take it to the
     *       flag base to craft the mat, {@link BeatPhase#READY_TO_USE};</li>
     *   <li>neither — go get a pressure plate, {@link BeatPhase#NEED_TO_FETCH}.</li>
     * </ol>
     */
    private static BeatPhase pressurePlatePhase(PhaseInputs in) {
        if (in.hasItem()) {
            return BeatPhase.READY_TO_PLACE;
        }
        if (in.hasPressurePlate()) {
            return BeatPhase.READY_TO_USE;
        }
        return BeatPhase.NEED_TO_FETCH;
    }

    public static Presentation present(ChickenBeatState state, PhaseInputs in) {
        return rowFor(state, activePhase(state, in));
    }

    private static Presentation rowFor(ChickenBeatState state, BeatPhase phase) {
        return switch (state) {
            case WAITING_FOR_STICK -> stickRow(phase);
            case WAITING_FOR_WAND_ON_CAMPFIRE -> wandOnCampfireRow(phase);
            case SUNSET_AND_MAP -> sunsetAndMapRow(phase);
            case WAITING_FOR_WALL_BLOCK -> wallBlockRow(phase);
            case WAITING_FOR_DOOR -> doorRow(phase);
            case WAITING_FOR_WAND_ON_DOOR -> wandOnDoorRow(phase);
            case WAITING_FOR_SIGN -> new Presentation(
                    Bubble.single(new ItemStack(Items.OAK_SIGN)), HINT_SIGN, PLAIN_SIGN);
            case WAITING_FOR_CHEST -> chestRow(phase);
            case WAITING_FOR_PRESSURE_PLATE -> pressurePlateRow(phase);
            case WAITING_FOR_VILLAGER_UI -> new Presentation(
                    Bubble.single(new ItemStack(Items.VILLAGER_SPAWN_EGG)),
                    HINT_VILLAGER_UI, PLAIN_VILLAGER_UI);
            case WAITING_FOR_FLAG_UI -> new Presentation(
                    Bubble.single(new ItemStack(BlocksInit.COBBLESTONE_TOWN_FLAG.get())),
                    HINT_FLAG_UI, PLAIN_FLAG_UI);
            case AWAITING_WORLDLY_SEEDS_DELIVERY -> new Presentation(
                    Bubble.throughWalls(new ItemStack(ItemsInit.WORLDLY_SEEDS.get())),
                    HINT_WORLDLY_SEEDS, PLAIN_WORLDLY_SEEDS);
            case COMPLETE, FORFEIT -> new Presentation(Bubble.HIDDEN, null, null);
        };
    }

    private static Presentation stickRow(BeatPhase phase) {
        return switch (phase) {
            case READY_TO_USE -> new Presentation(
                    Bubble.alternating(
                            new ItemStack(Items.STICK),
                            new ItemStack(BlocksInit.COBBLESTONE_TOWN_FLAG.get())),
                    HINT_STICK_USE, PLAIN_STICK);
            default -> new Presentation(
                    Bubble.single(new ItemStack(Items.STICK)),
                    HINT_STICK, PLAIN_STICK);
        };
    }

    private static Presentation wandOnCampfireRow(BeatPhase phase) {
        return switch (phase) {
            case READY_TO_USE -> new Presentation(
                    Bubble.alternating(
                            new ItemStack(ItemsInit.TOWN_WAND.get()),
                            new ItemStack(Items.CAMPFIRE)),
                    HINT_WAND_CAMPFIRE_USE, PLAIN_WAND_CAMPFIRE);
            default -> new Presentation(
                    Bubble.single(new ItemStack(ItemsInit.TOWN_WAND.get())),
                    HINT_WAND_CAMPFIRE, PLAIN_WAND_CAMPFIRE);
        };
    }

    private static Presentation wandOnDoorRow(BeatPhase phase) {
        return switch (phase) {
            case READY_TO_USE -> new Presentation(
                    Bubble.alternating(
                            new ItemStack(ItemsInit.TOWN_WAND.get()),
                            new ItemStack(Items.OAK_DOOR)),
                    HINT_WAND_DOOR_USE, PLAIN_WAND_DOOR);
            default -> new Presentation(
                    Bubble.single(new ItemStack(ItemsInit.TOWN_WAND.get())),
                    HINT_WAND_DOOR, PLAIN_WAND_DOOR);
        };
    }

    private static Presentation wallBlockRow(BeatPhase phase) {
        Bubble bubble = Bubble.single(new ItemStack(Items.COBBLESTONE));
        return switch (phase) {
            case READY_TO_PLACE -> new Presentation(bubble, HINT_WALL_BLOCK_WITH_ITEM, PLAIN_WALL_BLOCK);
            default -> new Presentation(bubble, HINT_WALL_BLOCK, PLAIN_WALL_BLOCK);
        };
    }

    private static Presentation chestRow(BeatPhase phase) {
        Bubble bubble = Bubble.single(new ItemStack(Items.CHEST));
        return switch (phase) {
            case READY_TO_PLACE -> new Presentation(bubble, HINT_CHEST_WITH_ITEM, PLAIN_CHEST);
            default -> new Presentation(bubble, HINT_CHEST, PLAIN_CHEST);
        };
    }

    private static Presentation pressurePlateRow(BeatPhase phase) {
        return switch (phase) {
            case NEED_TO_FETCH -> new Presentation(
                    Bubble.single(new ItemStack(Items.OAK_PRESSURE_PLATE)),
                    HINT_PRESSURE_PLATE, PLAIN_PRESSURE_PLATE);
            case READY_TO_USE -> new Presentation(
                    Bubble.alternating(
                            new ItemStack(Items.OAK_PRESSURE_PLATE),
                            new ItemStack(BlocksInit.COBBLESTONE_TOWN_FLAG.get())),
                    HINT_PRESSURE_PLATE_USE, PLAIN_PRESSURE_PLATE);
            default -> new Presentation(
                    Bubble.single(new ItemStack(ItemsInit.WELCOME_MAT_BLOCK.get())),
                    HINT_WELCOME_MAT, PLAIN_WELCOME_MAT);
        };
    }

    private static Presentation doorRow(BeatPhase phase) {
        Bubble bubble = Bubble.single(new ItemStack(Items.OAK_DOOR));
        return switch (phase) {
            case READY_TO_PLACE -> new Presentation(bubble, HINT_DOOR_WITH_ITEM, PLAIN_DOOR);
            default -> new Presentation(bubble, HINT_DOOR, PLAIN_DOOR);
        };
    }

    private static Presentation sunsetAndMapRow(BeatPhase phase) {
        return switch (phase) {
            case PREPARING -> new Presentation(
                    Bubble.single(new ItemStack(Items.CHEST)),
                    HINT_SUNSET_PREPARING, PLAIN_SUNSET_PREPARING);
            case READY_TO_USE -> new Presentation(
                    Bubble.alternating(
                            new ItemStack(ItemsInit.TOWN_WAND.get()),
                            new ItemStack(Items.CAMPFIRE)),
                    HINT_WAND_CAMPFIRE_USE, PLAIN_WAND_CAMPFIRE);
            default -> new Presentation(
                    Bubble.texture(ChickenArcBubbles.SUNSET_TEXTURE),
                    HINT_SUNSET, PLAIN_SUNSET);
        };
    }
}
