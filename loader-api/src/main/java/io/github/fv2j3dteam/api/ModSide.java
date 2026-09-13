package io.github.fv2j3dteam.api;

public enum ModSide {
    CLIENT("client"),
    SERVER("server"),
    BOTH("both");

    private final String value;

    ModSide(String value) {
        this.value = value;
    }

    public String value() { return value; }

    public static ModSide fromString(String s) {
        if (s == null) return BOTH;
        String lower = s.toLowerCase();
        return switch (lower) {
            case "client", "client_only" -> CLIENT;
            case "server", "dedicated_server", "dedicated" -> SERVER;
            default -> BOTH;
        };
    }

    public boolean isCompatibleWith(ModSide other) {
        return this == BOTH || other == BOTH || this == other;
    }
}
