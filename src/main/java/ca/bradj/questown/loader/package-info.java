/**
 * The mod-loader seam.
 *
 * <p>A <em>mod loader</em> is the framework that loads Questown into Minecraft: Forge on the 1.19.2
 * line, NeoForge on 1.20.6. It provides what a mod needs beyond vanilla: registering items, blocks,
 * entities and menus, config files, the network channel, the event bus, and capabilities such as
 * item handlers. Each loader has its own API for these.
 *
 * <p>The types in this package are <em>loader-neutral</em>: they name no Forge or NeoForge type, so
 * code written against them compiles under either loader. {@link ca.bradj.questown.loader.Loader}
 * is the entry point the rest of the mod calls; it forwards to the
 * {@link ca.bradj.questown.loader.LoaderApi} the active loader installs at startup (the
 * {@code loader.forge} or {@code loader.neo} package). Unit tests install {@code TestLoader}.
 *
 * <p>Minecraft-version differences are a separate seam: {@code ca.bradj.questown.mc}.
 */
package ca.bradj.questown.loader;
