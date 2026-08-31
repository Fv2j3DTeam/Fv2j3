package com.fv2j3.loader.core;

import com.fv2j3.api.ModContext;
import com.fv2j3.api.ModDescriptor;
import java.util.Map;

public final class ModContextFactory {
    private ModContextFactory() {
    }

    public static ModContext create(ModDescriptor descriptor, LoaderContext loaderContext, LoaderLogger logger, Map<String, Object> attributes) {
        return new ModContext(descriptor, loaderContext, logger, attributes);
    }
}
