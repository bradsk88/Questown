package ca.bradj.questown.commands.test;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class PlayerTestOutput implements TestOutput {

    private final ServerPlayer player;
    private final String prefix;

    public PlayerTestOutput(ServerPlayer player, String prefix) {
        this.player = player;
        this.prefix = prefix;
    }

    @Override
    public void msg(String text) {
        Compat.sendMessage(player, Component.literal("[" + prefix + "] " + text));
        QT.FLAG_LOGGER.info("[{}] {}", prefix, text);
    }

    @Override
    public void error(String text) {
        Compat.sendMessage(player, Component.literal("[" + prefix + " ERROR] " + text));
        QT.FLAG_LOGGER.error("[{}] {}", prefix, text);
    }
}
