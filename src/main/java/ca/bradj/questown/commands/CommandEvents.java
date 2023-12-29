package ca.bradj.questown.commands;

import ca.bradj.questown.Questown;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.command.ConfigCommand;

@Mod.EventBusSubscriber(modid = Questown.MODID)
public class CommandEvents {

    @SubscribeEvent
    public static void on(RegisterCommandsEvent event) {
        SetJobCommand.register(event.getDispatcher());
        TimeWarpCommand.register(event.getDispatcher());
        ConfigCommand.register(event.getDispatcher());
    }
}
