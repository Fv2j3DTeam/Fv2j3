package com.fv2j3.api;

public interface ModClassLoaderProvider {
    ClassLoader createClassLoader(ModContainer container);

    default ClassLoader getClassLoader(ModContainer container) {
        return createClassLoader(container);
    }

    default boolean supports(ModContainer container) {
        return container != null && container.descriptor() != null;
    }

    default void closeClassLoader(ModContainer container, ClassLoader classLoader) {
        if (classLoader == null) {
            return;
        }
        if (classLoader instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to close mod class loader for '" + container.id() + "'.", ex);
            }
        }
    }
}
