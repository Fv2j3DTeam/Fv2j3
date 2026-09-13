package io.github.fv2j3dteam.loader.core;

import io.github.fv2j3dteam.api.ModContext;
import io.github.fv2j3dteam.api.ModDescriptor;
import java.util.Map;

public final class ModContextFactory {
    private ModContextFactory() {
    }

    public static ModContext create(ModDescriptor descriptor, LoaderContext loaderContext, LoaderLogger logger, Map<String, Object> attributes) {
        return new ModContext(descriptor, loaderContext, logger, attributes);
    }
}
