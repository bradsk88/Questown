package ca.bradj.questown.mc;
import ca.bradj.questown.loader.Loader;

import ca.bradj.questown.Questown;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.network.OnScreenTextMessage;
import ca.bradj.questown.core.network.QuestownNetwork;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.mobs.visitor.VisitorMobRenderer;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

public class Util {

    public static long getTick(ServerLevel level) {
        return level.getGameTime();
    }

    public static Signals.DayTime getDayTime(Level serverLevel) {
        long dayTime = serverLevel.getDayTime();
        return UtilClean.getDayTime(dayTime);
    }

    public static <X> ImmutableMap<Integer, Supplier<X>> constant(ImmutableMap<Integer, X> constant) {
        ImmutableMap.Builder<Integer, Supplier<X>> b = ImmutableMap.builder();
        constant.forEach((k, v) -> b.put(k, () -> v));
        return b.build();
    }

    public static <X> ImmutableMap<Integer, X> realize(ImmutableMap<Integer, Supplier<X>> theoretical) {
        ImmutableMap.Builder<Integer, X> b = ImmutableMap.builder();
        theoretical.forEach((k, v) -> b.put(k, v.get()));
        return b.build();
    }

    public static <X> ImmutableList<X> realize(ImmutableList<Supplier<X>> theoretical) {
        ImmutableList.Builder<X> b = ImmutableList.builder();
        theoretical.forEach((v) -> b.add(v.get()));
        return b.build();
    }

    /**
     * @deprecated Use UtilClean version
     */
    public static <K, X, Y extends X> X getOrDefault(
            Map<K, X> map,
            K key,
            Y fallback
    ) {
        return UtilClean.getOrDefault(map, key, fallback);
    }

    /**
     * @deprecated Use UtilClean version
     */
    public static <K, X> ImmutableList<X> getOrDefaultCollection(
            Map<K, ? extends Collection<? extends X>> map,
            K key,
            ImmutableList<X> fallback
    ) {
        return UtilClean.getOrDefaultCollection(map, key, fallback);
    }

    /**
     * @deprecated Use UtilClean version
     */
    private static <K, X> Collection<X> getOrDefaultCollection(
            Map<K, ? extends Collection<? extends X>> map,
            K key,
            Collection<X> fallback,
            boolean mutable
    ) {
        return UtilClean.getOrDefaultCollection(map, key, fallback, mutable);
    }

    public static <K, V> Map<K, ArrayList<V>> makeMutable(Map<K, ? extends Collection<V>> inMap) {
        HashMap<K, ArrayList<V>> kArrayListHashMap = new HashMap<>();
        inMap.forEach((k, v) -> kArrayListHashMap.put(k, new ArrayList<>(v)));
        return kArrayListHashMap;
    }

    public static <X, Y> Y applyOrDefault(
            X param,
            Function<X, Y> fn,
            Y defaultt
    ) {
        Y v = fn.apply(param);
        if (v == null) {
            return defaultt;
        }
        return v;
    }

    public static <K, V> ImmutableMap<K, ImmutableList<V>> immutify(
            Map<K, ? extends Collection<V>> roomsNeedingIngredientsByState
    ) {
        ImmutableMap.Builder<K, ImmutableList<V>> b = ImmutableMap.builder();
        roomsNeedingIngredientsByState.forEach((k, v) -> b.put(k, ImmutableList.copyOf(v)));
        return b.build();
    }

    public static <K, V> Map<K, V> only(
            Map<K, V> map,
            K s
    ) {
        if (map.containsKey(s)) {
            return ImmutableMap.of(
                    s, map.get(s)
            );
        }
        return ImmutableMap.of();
    }

    public static <ITEM> Predicate<ITEM> funcToPredNullable(Function<ITEM, Boolean> ingredient) {
        if (ingredient == null) {
            return null;
        }
        return ingredient::apply;
    }

    public static <I extends Item<I>> WithReason<Boolean> anyMatch(
            Stream<I> stream,
            NoisyPredicate<I> check
    ) {
        List<WithReason<Boolean>> tests = stream.map(check::test).toList();
        for (WithReason<Boolean> test : tests) {
            if (test.value) {
                return test;
            }
        }
        String joined = String.join(", ", tests.stream().map(v -> v.reason).toList());
        return new WithReason<>(false, "All failed: [%s]", joined);
    }

    /**
     * @deprecated Probably shouldn't suppress noise
     */
    public static <ITEM> Predicate<ITEM> toQuiet(NoisyPredicate<ITEM> check) {
        return i -> check.test(i).value;
    }

    public static <TOWN_ITEM extends Item<TOWN_ITEM>> Collection<? extends NoisyBiPredicate<AmountHeld, TOWN_ITEM>> sameNoise(
            Collection<BiPredicate<AmountHeld, TOWN_ITEM>> all,
            String messageIfPass,
            String messageIfFail
    ) {
        ImmutableList.Builder<NoisyBiPredicate<AmountHeld, TOWN_ITEM>> b = ImmutableList.builder();
        all.forEach(bp -> {
            b.add((amountHeld, townItem) -> WithReason.bool(
                    bp.test(amountHeld, townItem),
                    messageIfPass,
                    messageIfFail,
                    townItem.getShortName()
            ));
        });
        return b.build();
    }

    /**
     * Handles potentially infinite iterables, applying the BASE_MAX_LOOP
     * to make them finite by truncating.
     */
    public static <X> void iterate(
            Iterable<X> source,
            Consumer<X> sink
    ) {
        Iterator<X> iter = source.iterator();
        for (int i = 0; i < Compat.configGet(Config.BASE_MAX_LOOP).get(); i++) {
            if (iter.hasNext()) {
                sink.accept(iter.next());
            } else {
                return;
            }
        }
    }

    public static <X> X getOrDefault(
            JsonObject object,
            String key,
            Function<JsonElement, X> puller,
            X fallback
    ) {
        if (!object.has(key)) {
            return fallback;
        }
        if (object.get(key).isJsonNull()) {
            return fallback;
        }
        return puller.apply(object.get(key));
    }

    public static void setProcessingStateOnProperty(
            ServerLevel level,
            IntegerProperty prop,
            State state,
            BlockPos pos
    ) {
        Integer maxPropVal = prop.getPossibleValues().stream().max(Integer::compare).orElse(0);
        int limited = Math.min(state.processingState(), maxPropVal);
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(prop, limited));
    }

    public static <K, V> void putIfAbsent(
            Map<K, V> qty,
            K k,
            V v
    ) {
        if (qty.get(k) != null) {
            return;
        }
        qty.put(k, v);
    }

    public static <X, Y> @Nullable Y orNull(
            @Nullable X input,
            Function<@NotNull X, Y> fn
    ) {
        return UtilClean.orNull(input, fn);
    }

    public static <X> void ifNotNull(
            X input,
            Consumer<X> fn
    ) {
        if (input == null) {
            return;
        }
        fn.accept(input);
    }

    public static <X, Y> Y withFallbackForNullInput(
            X input,
            Function<X, Y> fn,
            Y fallback
    ) {
        if (input == null) {
            return fallback;
        }
        return fn.apply(input);
    }

    public static Direction rotationToDirection(Integer value) {
        if (value == null) {
            return Direction.WEST;
        }
        int v = (value + 12) % 16;
        // 0 is south
        // 4 is west
        // 8 is north
        // 12 is east

        // 15, 0, 1 are south
        if (v == 15 || v < 2) {
            return Direction.SOUTH;
        }
        // 2, 3, 4, 5, 6 are west
        if (v < 7) {
            return Direction.WEST;
        }
        // 7, 8, 9 are north
        if (v < 10) {
            return Direction.NORTH;
        }
        // 10, 11, 12, 13, 14 are east
        return Direction.EAST;
    }

    public static <X> WorkOutput<X, WorkPosition<BlockPos>> workWithSurfaceInteractionPos(
            ServerLevel sl,
            WorkOutput<X, WorkPosition<BlockPos>> v
    ) {
        BlockPos feet = v.spot().entityFeetPos();
        if (sl.getBlockState(feet).isSolid()) {
            feet = feet.above();
        }
        return new WorkOutput<>(
                v.worked(),
                v.claimed(),
                v.town(),
                new WorkPosition<>(v.spot().jobBlock(), feet)
        );
    }


    /**
     * Force the default GUI blit state. 1.19.2's GuiComponent.blit set shader/blend/shaderColor
     * itself; the 1.20.6 immediate blit/fill path does not, so a prior draw (e.g. a dimming or
     * tinted fill) can leave the state such that the next blit renders wrong. Call before the
     * advancement widget frame/brace draws so they match 1.19.2 behaviour.
     */
    public static void blitState() {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void blit(
            GuiGraphics gui,
            ResourceLocation texture,
            int screenX,
            int screenY,
            float firstPixelInFilex,
            float firstPixelInFileY,
            int drawNumPixelsX,
            int drawNumPixelsY
    ) {
        // 1.20.6: blit(loc, x, y, u, v, width, height, sourceWidth, sourceHeight).
        // The 1.19.2 helper passed texW/texH = the draw size, so the whole native
        // texture fitted into the box; keep that by using the draw size as source size.
        // 1.20.6 blit(loc, x, y, u, v, W, H, SW, SH): uv in pixels, sheet size 256
        // (all questown sheet textures are 256x256).
        blitState();
        gui.blit(texture, screenX, screenY, firstPixelInFilex, firstPixelInFileY, drawNumPixelsX, drawNumPixelsY, 256, 256);
    }

    /**
     * Blit of a region of a texture that is not the 256x256 sheet, passing the texture's
     * true native size as "widthOfEntireFile"/"texFileHeight".
     *
     * <p>1.20.6's blit normalises u/v by that declared size ("u / texWidth") - see the
     * package-private "GuiGraphics.blit": with "256" the 32x32 job-status art sampled
     * only its leftmost 4 px and drew it stretched over the box (the "flat grey box"
     * status-icon bug). 1.19.2 passed the draw size; callers of 32x32 art pass "32, 32".
     */
    public static void blit(
            GuiGraphics gui,
            ResourceLocation texture,
            int screenX,
            int screenY,
            float firstPixelInFilex,
            float firstPixelInFileY,
            int drawNumPixelsX,
            int drawNumPixelsY,
            int widthOfEntireFile,
            int texFileHeight
    ) {
        blitState();
        gui.blit(texture, screenX, screenY, firstPixelInFilex, firstPixelInFileY, drawNumPixelsX, drawNumPixelsY, widthOfEntireFile, texFileHeight);
    }

    public static void blitTab(
            GuiGraphics gui,
            Integer x,
            Integer y,
            int i
    ) {
        // tabs.png is a 256x256 sheet of 16x16 icons; keep the explicit 256 source size.
        gui.blit(
                Questown.ResourceLocation("textures/menu/tabs.png"),
                x + 12,
                y + 10,
                i * 16,
                0,
                16,
                16,
                256,
                256
        );
    }

    public static void onScreenText(
            Supplier<ServerPlayer> player,
            String msgKey,
            Object... msgArgs
    ) {
        QuestownNetwork.sendToPlayer(player.get(),
                new OnScreenTextMessage(msgKey, msgArgs)
        );
    }

    public static final int faceWidth = 8;

    public static void blitFace(
            GuiGraphics gui,
            UUID uuid,
            int x,
            int y
    ) {
        blitFace(gui, uuid, x, y, 1);
    }

    public static void blitFace(
            GuiGraphics gui,
            UUID uuid,
            int x,
            int y,
            int scale
    ) {
        ResourceLocation texture = VisitorMobRenderer.getTextureLocation(uuid);
        // Player skins are 64x64; the face block sits at (8,8)-(16,16). The golden blits the
        // 8x8 face region and DISPLAYS it at 8*scale (magnified by `scale`). In 1.20.6 the
        // GuiGraphics.blit width/height are the REGION (blitted 1:1), so a larger display size
        // is not an argument -- it is a gui-pose scale. (Scaling the UV region instead grabs
        // `scale`x more of the skin and squishes the face; that was the port regression.)
        gui.pose().pushPose();
        gui.pose().translate(x, y, 0f);
        gui.pose().scale(scale, scale, 1f);
        gui.blit(texture, 0, 0, 8, 8, faceWidth, faceWidth, 64, 64);
        gui.pose().popPose();
    }

    public static <X> X ifNull(
            @Nullable X input,
            X fallback
    ) {
        return input == null ? fallback : input;
    }

    public static String toShortString(
            int containerSize,
            Function<Integer, ItemStack> getItem,
            boolean includeAir
    ) {
        ImmutableList.Builder<String> names = ImmutableList.builder();
        for (int i = 0; i < containerSize; i++) {
            net.minecraft.world.item.Item item = getItem.apply(i).getItem();
            if (Items.AIR.equals(item)) {
                if (!includeAir) {
                    continue;
                }
            }
            if (Loader.itemIdOf(item) == null) {
                names.add("<No ID>");
            } else {
                names.add(Loader.itemIdOf(item).getPath());
            }
        }
        return String.join(", ", names.build());
    }

    public static String getShortString(BlockPos target) {
        return String.format("[%d, %d ,%d]", target.getX(), target.getY(), target.getZ());
    }
    public static String getTinyString(BlockPos target) {
        return String.format("[%d, %d]", target.getX(), target.getZ());
    }

    public static WorkLocation.BlockInfo info(ServerLevel level) {
        return new WorkLocation.BlockInfo() {
            @Override
            public BlockState state(BlockPos bp) {
                return level.getBlockState(bp);
            }

            @Override
            public @Nullable BlockEntity entity(BlockPos bp) {
                return level.getBlockEntity(bp);
            }
        };
    }

    public static <X, Y> Y ifNullElse(
            @Nullable X texts,
            Supplier<Y> whenNull,
            Function<@NotNull X, Y> whenNotNull
    ) {
        return texts == null ? whenNull.get() : whenNotNull.apply(texts);
    }
}
