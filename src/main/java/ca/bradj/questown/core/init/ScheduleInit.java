package ca.bradj.questown.core.init;

import ca.bradj.questown.loader.Holder;
import ca.bradj.questown.loader.HolderRegistry;
import ca.bradj.questown.loader.Loader;
import net.minecraft.core.registries.Registries;

import ca.bradj.questown.Questown;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.world.entity.schedule.Schedule;

public class ScheduleInit {

    public static final HolderRegistry<Schedule> SCHEDULES = Loader.registry(Registries.SCHEDULES);

    public static final Holder<Schedule> VISITOR_SCHEDULE = SCHEDULES.register(
            VisitorMobEntity.DEFAULT_SCHEDULE_ID, () -> VisitorMobEntity.DEFAULT_SCHEDULE
    );

    public static void boot() {}
}
