package ca.bradj.questown.town;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TownVillagerHandle {

    final List<LivingEntity> entities = new ArrayList<>();

    public void tick() {

    }

    public Stream<LivingEntity> stream() {
        return entities.stream();
    }

    void remove(LivingEntity visitorMobEntity) {
        this.entities.remove(visitorMobEntity);
    }

    void forEach(Consumer<? super LivingEntity> c) {
        this.entities.forEach(c);
    }

    public boolean isEmpty() {
        return this.entities.isEmpty();
    }

    public long size() {
        return entities.size();
    }

    public void add(VisitorMobEntity vEntity) {
        this.entities.add(vEntity);
    }

    public boolean exists(VisitorMobEntity visitorMobEntity) {
        return entities.contains(visitorMobEntity);
    }
}
