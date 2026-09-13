package io.github.fv2j3dteam.loader.core;

public enum LoaderSide {
    CLIENT("client"),
    DEDICATED_SERVER("server"),
    BOTH("both");

    private final String value;

    LoaderSide(String value) {
        this.value = value;
    }

    public String value() { return value; }

    public static LoaderSide fromProperty(String name) {
        String prop = System.getProperty(name);
        return fromString(prop);
    }

    public static LoaderSide fromString(String s) {
        if (s == null) return BOTH;
        String lower = s.trim().toLowerCase();
        return switch (lower) {
            case "client", "fv2j3_client" -> CLIENT;
            case "server", "dedicated_server", "fv2j3_server", "dedicated" -> DEDICATED_SERVER;
            default -> BOTH;
        };
    }
}
