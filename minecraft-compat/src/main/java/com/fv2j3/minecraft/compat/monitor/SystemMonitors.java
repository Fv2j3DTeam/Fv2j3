package com.fv2j3.minecraft.compat.monitor;

/**
 * Selects the platform system monitor for the current OS.
 */
public final class SystemMonitors {
    private SystemMonitors() {
    }

    public static SystemMonitor create() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("linux")) {
            return new LinuxSystemMonitor();
        }
        if (os.contains("win")) {
            return new WindowsSystemMonitor();
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return new MacOSSystemMonitor();
        }
        return new UnknownSystemMonitor();
    }
}
