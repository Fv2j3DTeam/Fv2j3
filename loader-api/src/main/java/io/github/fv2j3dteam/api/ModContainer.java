package io.github.fv2j3dteam.api;

public interface ModContainer {
    ModDescriptor descriptor();

    default String id() {
        return descriptor().id();
    }

    default String sourceName() {
        return "";
    }

    default String sourceType() {
        return "";
    }

    default ModContainerState state() {
        return ModContainerState.DISCOVERED;
    }

    default ModContext context() {
        return null;
    }

    default Mod mod() {
        return null;
    }

    default ClassLoader classLoader() {
        return null;
    }

    default ModRuntime runtime() {
        return null;
    }

    default boolean hasRuntime() {
        return runtime() != null;
    }

    default void attachRuntime(ModRuntime runtime) {
    }

    default ClassLoaderOwnership ownership() {
        return ClassLoaderOwnership.forContainer(this);
    }

    default RuntimeClosePolicy closePolicy() {
        return RuntimeClosePolicy.BOTH;
    }

    default boolean requiresCloseOnStop() {
        return closePolicy() == RuntimeClosePolicy.ON_STOPPED || closePolicy() == RuntimeClosePolicy.BOTH;
    }

    default boolean requiresCloseOnFailure() {
        return closePolicy() == RuntimeClosePolicy.ON_FAILED || closePolicy() == RuntimeClosePolicy.BOTH;
    }
}
