package com.fv2j3.universe.environment;

import com.fv2j3.universe.identifiers.ChunkCoord;
import com.fv2j3.universe.identifiers.UniverseId;

/**
 * Environmental emitter contract (§31, §63). External systems (fire,
 * machines, volcanoes, etc.) register as emitters; the environmental
 * systems query them for their per-tick contribution to the fields.
 *
 * Emitters are deterministic given the same simulation epoch and chunk
 * coordinate; they own no global state.
 */
public interface EnvironmentalEmitter {

    /** Stable id of the emitter (e.g. fire source id, machine id). */
    long emitterId();

    /** Chunk this emitter is associated with. */
    ChunkCoord chunk();

    /** Local cell coordinates (chunk-local). */
    int cellX();
    int cellY();
    int cellZ();

    /** Per-tick heat output (K / s, applied to local temperature). */
    double heatOutputPerSec();

    /** Per-tick humidity contribution (kg/kg / s). */
    double humidityOutputPerSec();

    /** Per-tick CO2 contribution (Pa / s). */
    double co2OutputPerSec();

    /** Per-tick smoke/gas emission (smoke mass / s) per gasId. */
    double emissionRate(String gasId);

    /** Whether the emitter is still active. */
    boolean isActive();

    /** Planet (for routing) — emissions stay scoped to one body. */
    UniverseId planetId();
}
