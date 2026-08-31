package com.fv2j3.loader.core;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StandardLoaderLogger implements LoaderLogger {
    private final Logger delegate;

    public StandardLoaderLogger(String name) {
        this.delegate = Logger.getLogger(Objects.requireNonNullElse(name, "Fv2j3"));
    }

    @Override
    public void debug(String message) {
        delegate.fine(message);
    }

    @Override
    public void info(String message) {
        delegate.info(message);
    }

    @Override
    public void warn(String message) {
        delegate.warning(message);
    }

    @Override
    public void error(String message) {
        delegate.log(Level.SEVERE, message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        delegate.log(Level.SEVERE, message, throwable);
    }
}
