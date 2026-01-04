package ca.bradj.questown.town.entity;

public interface HealingModule<ENTITY> {
    Double getHealFactor(ENTITY e);
}
