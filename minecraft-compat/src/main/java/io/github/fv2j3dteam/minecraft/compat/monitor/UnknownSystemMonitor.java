package io.github.fv2j3dteam.minecraft.compat.monitor;

/**
 * Fallback for unrecognized platforms: only JVM memory and CPU load, all
 * GPU/temperature metrics N/A.
 */
final class UnknownSystemMonitor extends BaseSystemMonitor {
}
