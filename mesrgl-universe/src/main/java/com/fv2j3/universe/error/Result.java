package com.fv2j3.universe.error;

/**
 * Result type with explicit success/error semantics (§87). Avoids silent exception swallowing.
 * The system NEVER returns null to signal failure; this type or a thrown exception is always used.
 */
public final class Result<T> {
    private final T value;
    private final String error;
    private final Throwable cause;

    private Result(T value, String error, Throwable cause) {
        this.value = value;
        this.error = error;
        this.cause = cause;
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(value, null, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(null, message, null);
    }

    public static <T> Result<T> error(String message, Throwable cause) {
        return new Result<>(null, message, cause);
    }

    public boolean isOk() { return error == null; }
    public boolean isError() { return error != null; }
    public T value() {
        if (error != null) throw new IllegalStateException("Result is error: " + error);
        return value;
    }
    public T valueOr(T fallback) {
        return error == null ? value : fallback;
    }
    public String error() { return error; }
    public Throwable cause() { return cause; }
}
