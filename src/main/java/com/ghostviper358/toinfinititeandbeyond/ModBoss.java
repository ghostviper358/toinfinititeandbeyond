package com.ghostviper358.toinfinititeandbeyond;

/**
 * Marks the mod's bosses (Infinitite Monstrosity, Living Void, Universal Destroyer).
 *
 * Bosses can't be instantly killed or teleported away by the Universal Sword, or deleted by a
 * Universal God - otherwise one lucky hit or keypress would end a boss fight. Their boss bars
 * show {@link #getBossHealthFraction()}.
 */
public interface ModBoss {

    /** How much health the boss has left, from 0 to 1 - what its boss bar shows. */
    float getBossHealthFraction();
}
