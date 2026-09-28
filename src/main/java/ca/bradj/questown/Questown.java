package ca.bradj.questown;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.RecipeItemConfig;
import ca.bradj.questown.loader.Loader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.Logger;

/**
 * Loader-neutral mod bootstrap.
 *
 * <p>The per-loader {@code @Mod} entry (see {@code loader.neo.QuestownMod}) installs the
 * {@link Loader} facade, registers the config specs, defines the registries, and wires the
 * setup/client events onto the active loader's buses. This class holds only the shared,
 * loader-independent pieces: the mod id, logging, default item props, and the neutral
 * {@code onCommonSetup}/{@code onClientSetup} bodies.
 */
public class Questown {
    public static final String MODID = "questown";

    // Directly reference a log4j logger.
    /**
     * @deprecated Use QT.LOGGER
     */
    public static final Logger LOGGER = QT.LOGGER;
    public static final Item.Properties DEFAULT_ITEM_PROPS = new Item.Properties();
    public static ResourceLocation ResourceLocationError = ResourceLocation("error");

    public static ResourceLocation ResourceLocation(String id) {
        return new ResourceLocation(Questown.MODID, id);
    }

    public static ResourceLocation ResourceLocation(String id, Object... args) {
        return new ResourceLocation(Questown.MODID, String.format(id, args));
    }

    // ---- Neutral bootstrap, driven by the per-loader @Mod entry -----------

    public static void registerConfigs() {
        Loader.registerConfig(Loader.ConfigType.SERVER, Config.SPEC, Config.FILENAME);
        Loader.registerConfig(Loader.ConfigType.SERVER, RecipeItemConfig.SPEC, RecipeItemConfig.FILENAME);
    }

    public static void onCommonSetup() {
        ca.bradj.questown.core.network.QuestownNetwork.init();
        ca.bradj.questown.jobs.declarative.DeclarativeJobs.staticInitialize();
        ca.bradj.questown.jobs.Events.onCommonSetup();
    }


}
