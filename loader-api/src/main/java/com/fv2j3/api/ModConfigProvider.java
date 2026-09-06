package com.fv2j3.api;

import java.util.Optional;

/**
 * Resolves a mod's {@link Fv2j3Config} by mod id. The loader is
 * responsible for creating one config file per mod; two mods never
 * share a file. If the mod has not requested a config, the resolver
 * returns an empty optional.
 *
 * Mods typically obtain this provider through their {@link ModContext}:
 *
 *   {@code Fv2j3Config cfg = context.configProvider().resolve(myModId).orElseThrow();}
 */
@FunctionalInterface
public interface ModConfigProvider {
    Optional<Fv2j3Config> resolve(String modId);
}
