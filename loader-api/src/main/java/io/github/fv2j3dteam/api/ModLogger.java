package io.github.fv2j3dteam.api;

public interface ModLogger {
    void debug(String message);

    void info(String message);

    void warn(String message);

    void error(String message);

    default void error(String message, Throwable throwable) {
        error(message + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }
}
