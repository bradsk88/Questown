package ca.bradj.questown.commands;

import ca.bradj.questown.Questown;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.command.ConfigCommand;

@Mod.EventBusSubscriber(modid = Questown.MODID)
public class CommandInit {

    @SubscribeEvent
    public static void on(RegisterCommandsEvent event) {
        SetJobCommand.register(event.getDispatcher());
        TimeWarpCommand.register(event.getDispatcher());
        FreezeCommand.register(event.getDispatcher());
        ConfigCommand.register(event.getDispatcher());
        FlagCommand.register(event.getDispatcher());
        DebugCommand.register(event.getDispatcher());
        DebugDoorsCommand.register(event.getDispatcher());
        DebugAllDoorsCommand.register(event.getDispatcher());
        SpawnVillagerCommand.register(event.getDispatcher());
        AddDamageCommand.register(event.getDispatcher());
    }
}
