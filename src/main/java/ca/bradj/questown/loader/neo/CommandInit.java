package ca.bradj.questown.loader.neo;

import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.AddDamageCommand;
import ca.bradj.questown.commands.AddExperienceCommand;
import ca.bradj.questown.commands.ChangeJobCommand;
import ca.bradj.questown.commands.UnlockSkillCommand;
import ca.bradj.questown.commands.ChickenRemoveCommand;
import ca.bradj.questown.commands.DebugAllDoorsCommand;
import ca.bradj.questown.commands.DebugCommand;
import ca.bradj.questown.commands.DebugDoorsCommand;
import ca.bradj.questown.commands.DevFlagDebugToggleCommand;
import ca.bradj.questown.commands.DevFlagDebugTurboCommand;
import ca.bradj.questown.commands.DevLootFireCommand;
import ca.bradj.questown.commands.DevWorkScoreCommand;
import ca.bradj.questown.commands.DevMakeStarvingCommand;
import ca.bradj.questown.commands.DevQuestAddItemCommand;
import ca.bradj.questown.commands.DevQuestDeserializeCommand;
import ca.bradj.questown.commands.DrainTimersCommand;
import ca.bradj.questown.commands.FindFlagCommand;
import ca.bradj.questown.commands.FlagBuildRoomCommand;
import ca.bradj.questown.commands.FlagCommand;
import ca.bradj.questown.commands.FlagCraftCommand;
import ca.bradj.questown.commands.FlagDestroyCommand;
import ca.bradj.questown.commands.FlagPlaceBlockCommand;
import ca.bradj.questown.commands.FlagPlaceBoardCommand;
import ca.bradj.questown.commands.VillagerStatsCommand;
import ca.bradj.questown.commands.FlagPlaceDoorCommand;
import ca.bradj.questown.commands.FlagPlacePlayerCommand;
import ca.bradj.questown.commands.FlagShutdownCommand;
import ca.bradj.questown.commands.FreeFoodCommand;
import ca.bradj.questown.commands.FreezeCommand;
import ca.bradj.questown.commands.LogDataCommand;
import ca.bradj.questown.commands.LookRoomCommand;
import ca.bradj.questown.commands.PrepareWorkCommand;
import ca.bradj.questown.commands.QtDevClickCommand;
import ca.bradj.questown.commands.QtDevHelpCommand;
import ca.bradj.questown.commands.QtDevInspectCommand;
import ca.bradj.questown.commands.QtDevSpawnChickenCommand;
import ca.bradj.questown.commands.RegisterRoomCommand;
import ca.bradj.questown.commands.SetJobCommand;
import ca.bradj.questown.commands.SpawnVillagerCommand;
import ca.bradj.questown.commands.SupplySwordCommand;
import ca.bradj.questown.commands.TestAllCommand;
import ca.bradj.questown.commands.TestCommand;
import ca.bradj.questown.commands.TimeWarpCommand;
import ca.bradj.questown.commands.ToggleHungerConfig;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = Questown.MODID)
public class CommandInit {

    @SubscribeEvent
    public static void on(RegisterCommandsEvent event) {
        // Dev commands
        DevQuestAddItemCommand.register(event.getDispatcher());
        DevQuestDeserializeCommand.register(event.getDispatcher());
        DevFlagDebugToggleCommand.register(event.getDispatcher(), event.getBuildContext());
        DevMakeStarvingCommand.register(event.getDispatcher());
        DevFlagDebugTurboCommand.register(event.getDispatcher());
        DevLootFireCommand.register(event.getDispatcher());
        DevWorkScoreCommand.register(event.getDispatcher());

        // Player commands
        SetJobCommand.register(event.getDispatcher(), event.getBuildContext());
        TimeWarpCommand.register(event.getDispatcher());
        FreezeCommand.register(event.getDispatcher());
        FlagCommand.register(event.getDispatcher());
        FlagDestroyCommand.register(event.getDispatcher());
        FlagShutdownCommand.register(event.getDispatcher());
        DebugCommand.register(event.getDispatcher());
        DebugDoorsCommand.register(event.getDispatcher());
        FindFlagCommand.register(event.getDispatcher());
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
        RegisterRoomCommand.register(event.getDispatcher());
        QtDevInspectCommand.register(event.getDispatcher(), event.getBuildContext());
        FlagCraftCommand.register(event.getDispatcher());
        FlagPlaceBoardCommand.register(event.getDispatcher());
        VillagerStatsCommand.register(event.getDispatcher());
        FlagPlaceDoorCommand.register(event.getDispatcher());
        FlagBuildRoomCommand.register(event.getDispatcher());
        QtDevHelpCommand.register(event.getDispatcher());
        QtDevSpawnChickenCommand.register(event.getDispatcher());
        QtDevClickCommand.register(event.getDispatcher(), event.getBuildContext());
        FlagPlacePlayerCommand.register(event.getDispatcher());
        FreeFoodCommand.register(event.getDispatcher());
        SupplySwordCommand.register(event.getDispatcher());
        LookRoomCommand.register(event.getDispatcher());
        ChangeJobCommand.register(event.getDispatcher());
        UnlockSkillCommand.register(event.getDispatcher());
        FlagPlaceBlockCommand.register(event.getDispatcher());
    }
}
