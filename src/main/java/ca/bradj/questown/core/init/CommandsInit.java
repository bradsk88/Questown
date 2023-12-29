package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.commands.JobArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CommandsInit {

    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_TYPES = DeferredRegister.create(
            ForgeRegistries.COMMAND_ARGUMENT_TYPES,
            Questown.MODID
    );

    public static final RegistryObject<ArgumentTypeInfo<JobArgument, ?>> SETJOB = COMMAND_TYPES.register(
            "setjob",
            () -> ArgumentTypeInfos.registerByClass(JobArgument.class, SingletonArgumentInfo.contextAware(JobArgument::new))
    );

    public static void register(IEventBus bus) {
        COMMAND_TYPES.register(bus);
    }
}
