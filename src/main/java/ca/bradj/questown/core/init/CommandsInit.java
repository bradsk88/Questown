package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;
import net.minecraft.core.registries.Registries;

import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.commands.JobArgument;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;

public class CommandsInit {

    public static final HolderRegistry<ArgumentTypeInfo<?, ?>> COMMAND_TYPES = Loader.registry(Registries.COMMAND_ARGUMENT_TYPE);

    public static final Holder<ArgumentTypeInfo<JobArgument, ?>> SET_JOB = COMMAND_TYPES.register(
            "setjob",
            () -> ArgumentTypeInfos.registerByClass(JobArgument.class, SingletonArgumentInfo.contextAware(JobArgument::new))
    );
    public static final Holder<ArgumentTypeInfo<DebugLogArgument, ?>> TOGGLE_DEBUG = COMMAND_TYPES.register(
            "toggle_debug",
            () -> ArgumentTypeInfos.registerByClass(DebugLogArgument.class, SingletonArgumentInfo.contextAware(DebugLogArgument::new))
    );

    public static void boot() {}
}
