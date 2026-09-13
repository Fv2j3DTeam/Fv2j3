package io.github.fv2j3dteam.loader.core;

import java.util.List;

public interface ModDiscovery {
    List<ModCandidate> discover(ModSource source);
}
