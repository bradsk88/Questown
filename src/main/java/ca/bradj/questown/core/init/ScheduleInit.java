package ca.bradj.questown.core.init;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ScheduleInit {

    public static final DeferredRegister<Schedule> SCHEDULES = DeferredRegister.create(
            ForgeRegistries.SCHEDULES,
            Questown.MODID
    );

    public static final RegistryObject<Schedule> VISITOR_SCHEDULE = SCHEDULES.register(
            VisitorMobEntity.DEFAULT_SCHEDULE_ID, () -> VisitorMobEntity.DEFAULT_SCHEDULE
    );

    public static void register(IEventBus bus) {
        SCHEDULES.register(bus);
    }
}
