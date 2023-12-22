package ca.bradj.questown.town.interfaces;

public interface MutableWorkStatusHandle<ROOM, TICK_SOURCE, POS, ITEM> extends WorkStatusHandle<POS, ITEM>, TimerHandle<ROOM, TICK_SOURCE> {

}
