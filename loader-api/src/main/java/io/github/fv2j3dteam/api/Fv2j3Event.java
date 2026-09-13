package io.github.fv2j3dteam.api;

public final class Fv2j3Event {
    private final String modId;
    private final String phase;
    private final long timestamp;

    public Fv2j3Event(String modId, String phase) {
        this.modId = modId;
        this.phase = phase;
        this.timestamp = System.nanoTime();
    }

    public String modId() { return modId; }
    public String phase() { return phase; }
    public long timestamp() { return timestamp; }

    @Override public String toString() {
        return "Fv2j3Event{modId='" + modId + "', phase='" + phase + "'}";
    }
}
