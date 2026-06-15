package ca.bradj.questown.commands;

import ca.bradj.questown.Questown;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Questown.MODID)
public class CommandInit {

    @SubscribeEvent
    public static void on(RegisterCommandsEvent event) {
        // Dev commands
        DevQuestAddItemCommand.register(event.getDispatcher());
        DevQuestDeserializeCommand.register(event.getDispatcher());
        DevFlagDebugToggleCommand.register(event.getDispatcher(), event.getBuildContext());
        DevMakeStarvingCommand.register(event.getDispatcher());
        DevFlagDebugTurboCommand.register(event.getDispatcher());

        // Player commands
        SetJobCommand.register(event.getDispatcher(), event.getBuildContext());
        TimeWarpCommand.register(event.getDispatcher());
        FreezeCommand.register(event.getDispatcher());
        FlagCommand.register(event.getDispatcher());
        FlagDestroyCommand.register(event.getDispatcher());
        FlagShutdownCommand.register(event.getDispatcher());
        DebugCommand.register(event.getDispatcher());
        DebugDoorsCommand.register(event.getDispatcher());
        DebugAllDoorsCommand.register(event.getDispatcher());
        SpawnVillagerCommand.register(event.getDispatcher());
        AddDamageCommand.register(event.getDispatcher());
        AddExperienceCommand.register(event.getDispatcher());
        DrainTimersCommand.register(event.getDispatcher());
        PrepareWorkCommand.register(event.getDispatcher());
        LogDataCommand.register(event.getDispatcher());
        ToggleHungerConfig.register(event.getDispatcher());
        TestCommand.register(event.getDispatcher(), event.getBuildContext());
        TestAllCommand.register(event.getDispatcher(), event.getBuildContext());
        ChickenRemoveCommand.register(event.getDispatcher());
    }
}
