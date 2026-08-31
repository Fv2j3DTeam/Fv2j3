package com.fv2j3.minecraft.compat.monitor;

/**
 * Fallback for unrecognized platforms: only JVM memory and CPU load, all
 * GPU/temperature metrics N/A.
 */
final class UnknownSystemMonitor extends BaseSystemMonitor {
}
