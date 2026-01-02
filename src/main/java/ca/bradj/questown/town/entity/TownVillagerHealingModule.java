package ca.bradj.questown.town.entity;

import ca.bradj.questown.mobs.visitor.VisitorMobEntity;

public class TownVillagerHealingModule implements HealingModule<VisitorMobEntity> {
    @Override
    public Double getHealFactor(VisitorMobEntity e) {
        return 0.0;
    }
}
