package io.github.fv2j3dteam.loader.runtime;

/**
 * @deprecated Use {@link ClientBootstrap} for the Minecraft Client or {@link ServerBootstrap} for the
 *             Minecraft Dedicated Server. This class is retained for backward compatibility with
 *             launchers that point at the historical entry point.
 */
@Deprecated
public final class Bootstrap {
    private Bootstrap() {
    }

    public static void main(String[] args) {
        ClientBootstrap.main(args);
    }
}
