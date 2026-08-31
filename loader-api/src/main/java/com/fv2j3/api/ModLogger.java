package com.fv2j3.api;

public interface ModLogger {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    default void error(String message, Throwable throwable) {
        error(message + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }
}
