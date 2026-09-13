package io.github.fv2j3dteam.universe.generation;

import io.github.fv2j3dteam.universe.identifiers.UniverseId;
import io.github.fv2j3dteam.universe.math.XorShift64;
import io.github.fv2j3dteam.universe.seeds.SeedDerivation;
import io.github.fv2j3dteam.universe.universe.AsteroidBelt;
import io.github.fv2j3dteam.universe.universe.Station;
import io.github.fv2j3dteam.universe.universe.Star;
import io.github.fv2j3dteam.universe.universe.StarSystem;

import java.util.ArrayList;
import java.util.List;

/** Star system generator (§8). Generates stars, planets, moons, asteroid belts, stations. */
public final class StarSystemGenerator {

    public static final String STAGE = "star-system";

    public StarSystem generate(UniverseId universeId, long universeRootSeed, long galaxyIndex, long systemIndex) {
        UniverseId sid = universeId.withGalaxy(galaxyIndex).withSystem(systemIndex);
        long seed = SeedDerivation.deriveSeed(universeRootSeed, sid);
        XorShift64 rng = new XorShift64(seed);

        // Position in galaxy (spiral-ish distribution)
        double armOffset = (systemIndex % 4) * (Math.PI * 0.5);
        double theta = (systemIndex * 0.13) + armOffset;
        double radius = 1000.0 + 8000.0 * rng.nextDouble();
        double px = Math.cos(theta) * radius;
        double py = (rng.nextDouble() - 0.5) * 200.0;
        double pz = Math.sin(theta) * radius;

        // Stars: 1-3
        int starCount = 1 + rng.nextInt(0, 3);
        if (rng.nextDouble() < 0.08) starCount = 0; // rogue planet chance
        List<UniverseId> starIds = new ArrayList<>(starCount);
        for (int i = 0; i < starCount; i++) starIds.add(sid.withBody(-100L - i));

        // Planets: 0-12
        int planetCount = rng.nextInt(0, 13);
        List<UniverseId> planetIds = new ArrayList<>(planetCount);
        for (int i = 0; i < planetCount; i++) planetIds.add(sid.withBody(i + 1L));

        // Moons: 0-3 per planet (deterministic, encoded into the planet seed)
        int moonCount = 0;
        for (UniverseId p : planetIds) {
            XorShift64 prng = new XorShift64(p.stableHash());
            moonCount += prng.nextInt(0, 4);
        }
        List<UniverseId> moonIds = new ArrayList<>(moonCount);
        long bodyCursor = 1000L;
        for (UniverseId p : planetIds) {
            XorShift64 prng = new XorShift64(p.stableHash());
            int n = prng.nextInt(0, 4);
            for (int i = 0; i < n; i++) {
                moonIds.add(p.withBody(bodyCursor++));
            }
        }

        // Asteroid belts: 0-2
        int beltCount = rng.nextInt(0, 3);
        List<UniverseId> beltIds = new ArrayList<>(beltCount);
        for (int i = 0; i < beltCount; i++) beltIds.add(sid.withBody(-200L - i));

        // Stations: 0-2
        int stationCount = rng.nextInt(0, 3);
        List<UniverseId> stationIds = new ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) stationIds.add(sid.withBody(-300L - i));

        return new StarSystem(sid, seed, starIds, planetIds, moonIds, beltIds, stationIds, px, py, pz);
    }
}
