package com.fv2j3.loader.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModClassLoader extends URLClassLoader {
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "com.fv2j3.",
            "net.minecraft."
    );

    private final List<ClassLoader> dependencyLoaders;
    private final Path sourcePath;

    public ModClassLoader(Path sourcePath, ClassLoader parent, List<ClassLoader> dependencyLoaders) {
        super(toUrls(sourcePath), parent);
        this.sourcePath = sourcePath;
        this.dependencyLoaders = dependencyLoaders == null ? List.of() : List.copyOf(dependencyLoaders);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) {
            if (isProtectedNamespace(name)) {
                try {
                    loaded = getParent().loadClass(name);
                } catch (ClassNotFoundException ex) {
                    throw new ClassNotFoundException("Protected package '%s' cannot be defined by mod '%s'.".formatted(name, sourcePath), ex);
                }
            } else {
                for (ClassLoader dependencyLoader : dependencyLoaders) {
                    if (dependencyLoader == null) {
                        continue;
                    }
                    try {
                        loaded = dependencyLoader.loadClass(name);
                        if (resolve) {
                            resolveClass(loaded);
                        }
                        return loaded;
                    } catch (ClassNotFoundException ignored) {
                        // not in this dependency layer; continue to parent and local loading
                    }
                }
                try {
                    loaded = super.loadClass(name, resolve);
                    return loaded;
                } catch (ClassNotFoundException ex) {
                    if (isProtectedNamespace(name)) {
                        throw ex;
                    }
                    loaded = findClass(name);
                }
            }
        }
        if (resolve) {
            resolveClass(loaded);
        }
        return loaded;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (isProtectedNamespace(name)) {
            throw new ClassNotFoundException("Protected package '%s' cannot be defined by a mod classloader.".formatted(name));
        }
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException ex) {
            throw new ClassNotFoundException("Failed to load class '" + name + "' from mod source '" + sourcePath + "'.", ex);
        }
    }

    @Override
    public URL getResource(String name) {
        for (ClassLoader dependencyLoader : dependencyLoaders) {
            if (dependencyLoader == null) {
                continue;
            }
            URL resource = dependencyLoader.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        URL resource = getParent() == null ? null : getParent().getResource(name);
        if (resource != null) {
            return resource;
        }
        return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL resource = getResource(name);
        if (resource == null) {
            return null;
        }
        try {
            return resource.openStream();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to open resource '" + name + "' from mod source '" + sourcePath + "'.", ex);
        }
    }

    private static URL[] toUrls(Path sourcePath) {
        if (sourcePath == null) {
            throw new IllegalArgumentException("Mod source path is required.");
        }
        if (Files.isDirectory(sourcePath)) {
            try {
                return new URL[] { sourcePath.toUri().toURL() };
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to build mod classpath for directory '" + sourcePath + "'.", ex);
            }
        }
        if (Files.isRegularFile(sourcePath)) {
            try {
                return new URL[] { sourcePath.toUri().toURL() };
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to build mod classpath for jar '" + sourcePath + "'.", ex);
            }
        }
        throw new IllegalArgumentException("Mod source does not exist or is not a readable directory/jar: " + sourcePath);
    }

    private static boolean isProtectedNamespace(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
