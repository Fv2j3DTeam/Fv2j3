# Fv2j3 Minecraft Mod Loader

Fv2j3 is an independent Minecraft 1.12.2 Mod Loader providing a complete modding platform with stable APIs, mod discovery, metadata parsing, dependency resolution, classloader isolation, lifecycle management, event infrastructure, resource handling, configuration management, diagnostics, GUI integration, developer tooling, MDK, Gradle integration, example mods, automated testing, large-scale mod stress testing, and comprehensive documentation.

## Version

- Minecraft: **1.12.2**
- Java: **26**
- Fv2j3: **0.1.0-SNAPSHOT**

## Features

### Core Loader
- Deterministic loader state machine (CREATED → INITIALIZING → INITIALIZED → STARTING → RUNNING → STOPPING → STOPPED)
- Mod discovery from JAR files and directories
- Full mod metadata parsing and validation
- Dependency resolution with version constraints
- Per-mod ClassLoader isolation
- Lifecycle management (onLoad, onInitialize, onStart, onStop)
- Failure isolation and clear diagnostics
- Clean shutdown with JVM shutdown hook support

### Mod API
- `Fv2j3Registries`: Items, Blocks, Creative Tabs
- `Fv2j3Events`: Client/Server lifecycle events
- `Fv2j3Config`: Per-mod configuration
- `Fv2j3Item`, `Fv2j3Block`, `Fv2j3CreativeTab`: Content interfaces
- `ModLogger`: Mod-specific logging

### Minecraft Integration
- Real Minecraft 1.12.2 launch
- Forge-style loading screen with progress
- Main menu integration with Mods button
- Polished Mod GUI with:
  - Settings-style background
  - Left-positioned Back button
  - ESC navigation
  - Hover effects
  - Scrollable mod list
  - Mod name, ID, and status display
- Clean transition from loading to main menu

### Developer Platform
- MDK (Mod Development Kit) with Gradle wrapper
- Example mod with items, blocks, and creative tab
- 310 generated stress test mods
- Documentation generator
- Tutorial generator
- Comprehensive test suite

## Quick Start

### Build

```bash
./gradlew clean build --no-daemon
```

### Run Minecraft

```bash
./gradlew runMinecraft
```

### Generate Documentation

```bash
./gradlew generateDocumentation
# Output: build/docs/FVLIF3-DOCUMENTATION.md
```

### Generate Tutorial

```bash
./gradlew generateTutorial
# Output: build/tutorial/FVLIF3-TUTORIAL.md
```

### Run Stress Test

```bash
./gradlew stressTest
# Generates 310 test mods with 2000 items, 2000 blocks, 200 tabs
```

## Project Structure

```
Fv2j3/
├── loader-api/          # Public Mod API
│   └── src/main/java/com/fv2j3/api/
│       ├── Fv2j3Item.java
│       ├── Fv2j3Block.java
│       ├── Fv2j3CreativeTab.java
│       ├── Fv2j3Registries.java
│       ├── Fv2j3Events.java
│       ├── Fv2j3Config.java
│       ├── Mod.java
│       ├── ModContext.java
│       ├── ModDescriptor.java
│       └── ModDependency.java
├── loader-core/         # Loader implementation
│   └── src/main/java/com/fv2j3/loader/core/
│       ├── Fv2j3Loader.java
│       ├── LoaderContext.java
│       ├── LoaderState.java
│       ├── ModRegistry.java
│       ├── ModMetadataParser.java
│       ├── ModDependencyGraph.java
│       └── DocumentationGenerator.java
├── loader-runtime/      # Bootstrap entry point
│   └── src/main/java/com/fv2j3/loader/runtime/
│       ├── Bootstrap.java
│       ├── StressModGenerator.java
│       └── StressTestRunner.java
├── minecraft-compat/   # Minecraft 1.12.2 integration
│   └── src/main/java/com/fv2j3/minecraft/compat/
│       ├── MinecraftBootstrap.java
│       └── MinecraftModMenuTransformer.java
├── mdk/                # Mod Development Kit
│   ├── build.gradle
│   ├── gradlew
│   ├── gradlew.bat
│   ├── gradle/wrapper/
│   └── src/main/java/com/example/mod/ExampleMod.java
├── docs/               # Architecture documentation
├── build.gradle         # Root build configuration
└── README.md
```

## Mod Metadata

```json
{
  "modId": "example_mod",
  "version": "1.0.0",
  "name": "Example Mod",
  "description": "A mod example.",
  "authors": ["Author"],
  "license": "MIT",
  "dependencies": [],
  "optionalDependencies": [],
  "entrypoint": "com.example.mod.ExampleMod"
}
```

## Example Mod

```java
package com.example.mod;

import com.fv2j3.api.*;

public final class ExampleMod implements Mod {
    private static final ModDescriptor DESCRIPTOR = new ModDescriptor(
        "example",
        "1.0.0",
        "Example Mod",
        "Example Fv2j3 mod.",
        java.util.List.of("Author"),
        "MIT",
        java.util.List.of(),
        java.util.List.of(),
        ">=0.1.0",
        "com.example.mod.ExampleMod"
    );

    @Override
    public ModDescriptor descriptor() { return DESCRIPTOR; }

    @Override
    public void onLoad(ModContext context) {
        context.logger().info("Example Mod loaded.");

        Fv2j3Registries.registerItem("example", "example_item",
            (Fv2j3Item) () -> "example_item".equals(id()) ? "Example Item" : "" : null);
        // Register blocks, creative tabs, etc.
    }

    @Override
    public void onInitialize(ModContext context) { }
    @Override
    public void onStart(ModContext context) { }
    @Override
    public void onStop(ModContext context) { }
}
```

## Stress Test Results

```
Fv2j3 Stress Test Summary
=========================
Total Mods: 310
Creative Tabs: 200
Items: 1000
Blocks: 1000
Location: build/stress-jars

Stress Test Report
========================
Mods processed: 310
Creative Tabs: 200
Items: 1000
Blocks: 1000
Registry calls: 2200
Event listeners: 102
Total time: 149 ms
Result: PASS
```

## Tests

```bash
# Run all tests
./gradlew test

# Run stress test
./gradlew stressTest

# Test MDK
./gradlew testMdk
```

## Gradle Tasks

| Task | Description |
|------|-------------|
| `./gradlew build` | Build all modules |
| `./gradlew test` | Run unit tests |
| `./gradlew runMinecraft` | Launch Minecraft with Fv2j3 |
| `./gradlew generateDocumentation` | Generate technical docs |
| `./gradlew generateTutorial` | Generate tutorial |
| `./gradlew stressTest` | Run 310-mod stress test |
| `./gradlew testMdk` | Verify MDK structure |

## Java 26

Fv2j3 targets Java 26 and maintains a modern JDK toolchain while treating Minecraft 1.12.2 as a compatibility boundary.

## License

This is a research and development project for Minecraft 1.12.2 mod loader technology.
