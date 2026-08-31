package com.fv2j3.loader.core;

import java.util.List;

public interface ModDiscovery {
    List<ModCandidate> discover(ModSource source);
}
