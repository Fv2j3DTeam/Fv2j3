package com.fv2j3.loader.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DocumentationGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DocumentationGenerator <outputDir>");
            System.exit(1);
        }
        Path outputDir = Paths.get(args[0]);
        Files.createDirectories(outputDir);

        StringBuilder docs = new StringBuilder();
        docs.append("# Fv2j3 Technical Documentation\n\n");
        docs.append("Generated: " + new java.util.Date() + "\n\n");

        docs.append("## Table of Contents\n\n");
        docs.append("1. [Architecture](#architecture)\n");
        docs.append("2. [Mod Metadata](#mod-metadata)\n");
        docs.append("3. [Lifecycle](#lifecycle)\n");
        docs.append("4. [Dependencies](#dependencies)\n");
        docs.append("5. [ClassLoader](#classloader)\n");
        docs.append("6. [Registries](#registries)\n");
        docs.append("7. [Items](#items)\n");
        docs.append("8. [Blocks](#blocks)\n");
        docs.append("9. [Creative Tabs](#creative-tabs)\n");
        docs.append("10. [Events](#events)\n");
        docs.append("11. [Configuration](#configuration)\n");
        docs.append("12. [Logging](#logging)\n");
        docs.append("13. [Error Handling](#error-handling)\n");
        docs.append("14. [Resources](#resources)\n");
        docs.append("15. [Testing](#testing)\n");
        docs.append("16. [MDK](#mdk)\n\n");

        docs.append("## Architecture\n\n");
        docs.append("Fv2j3 is a modular Minecraft 1.12.2 mod loader built on Java 26.\n\n");
        docs.append("### Components\n\n");
        docs.append("- **loader-api**: Public Mod API contracts\n");
        docs.append("- **loader-core**: Loader implementation\n");
        docs.append("- **loader-runtime**: Bootstrap entry point\n");
        docs.append("- **minecraft-compat**: Minecraft 1.12.2 integration\n\n");

        docs.append("### Bootstrap Flow\n\n");
        docs.append("```\n");
        docs.append("Bootstrap.main()\n");
        docs.append("  -> Fv2j3Loader.initialize()\n");
        docs.append("     -> Mod discovery\n");
        docs.append("     -> Metadata parsing\n");
        docs.append("     -> Dependency resolution\n");
        docs.append("  -> Fv2j3Loader.start()\n");
        docs.append("     -> ModClassLoader creation\n");
        docs.append("     -> Mod instantiation\n");
        docs.append("     -> Lifecycle invocation\n");
        docs.append("  -> Minecraft integration\n");
        docs.append("```\n\n");

        docs.append("## Mod Metadata\n\n");
        docs.append("Mods require a `fv2j3.mod.json` metadata file in `META-INF/`.\n\n");
        docs.append("```json\n");
        docs.append("{\n");
        docs.append("  \"modId\": \"example_mod\",\n");
        docs.append("  \"version\": \"1.0.0\",\n");
        docs.append("  \"name\": \"Example Mod\",\n");
        docs.append("  \"description\": \"A mod example.\",\n");
        docs.append("  \"authors\": [\"Author\"],\n");
        docs.append("  \"license\": \"MIT\",\n");
        docs.append("  \"dependencies\": [],\n");
        docs.append("  \"optionalDependencies\": [],\n");
        docs.append("  \"entrypoint\": \"com.example.mod.ExampleMod\"\n");
        docs.append("}\n");
        docs.append("```\n\n");

        docs.append("### Mod ID Rules\n\n");
        docs.append("- Lowercase alphanumeric and underscores\n");
        docs.append("- No whitespace or special characters\n");
        docs.append("- Unique across all mods\n\n");

        docs.append("## Lifecycle\n\n");
        docs.append("Mods implement the `Mod` interface with lifecycle hooks:\n\n");
        docs.append("- `onLoad()`: Called during load phase\n");
        docs.append("- `onInitialize()`: Called during init phase\n");
        docs.append("- `onStart()`: Called when mod starts\n");
        docs.append("- `onStop()`: Called when mod stops\n\n");

        docs.append("## Dependencies\n\n");
        docs.append("Mods can declare dependencies:\n\n");
        docs.append("```java\n");
        docs.append("List.of(new ModDependency(\"other_mod\", \">=1.0.0\", ModDependency.Type.REQUIRED))\n");
        docs.append("```\n\n");
        docs.append("Version constraints: `>=1.0.0`, `<2.0.0`, `>=1.0.0 <2.0.0`\n\n");

        docs.append("## ClassLoader\n\n");
        docs.append("Each mod gets its own ClassLoader with controlled visibility:\n\n");
        docs.append("- Can see Java standard library\n");
        docs.append("- Can see Minecraft 1.12.2 classes\n");
        docs.append("- Can see Fv2j3 API\n");
        docs.append("- Can see declared dependencies\n\n");

        docs.append("## Registries\n\n");
        docs.append("Fv2j3 provides registries through `Fv2j3Registries`:\n\n");
        docs.append("- `ITEMS`: Item registry\n");
        docs.append("- `BLOCKS`: Block registry\n");
        docs.append("- `CREATIVE_TABS`: Creative tab registry\n\n");

        docs.append("## Items\n\n");
        docs.append("Create items by implementing `Fv2j3Item`:\n\n");
        docs.append("```java\n");
        docs.append("Fv2j3Registries.registerItem(\"modid\", \"my_item\", new Fv2j3Item() {\n");
        docs.append("    public String id() { return \"my_item\"; }\n");
        docs.append("    public String name() { return \"My Item\"; }\n");
        docs.append("    public int maxStackSize() { return 64; }\n");
        docs.append("});\n");
        docs.append("```\n\n");

        docs.append("## Blocks\n\n");
        docs.append("Create blocks by implementing `Fv2j3Block`:\n\n");
        docs.append("```java\n");
        docs.append("Fv2j3Registries.registerBlock(\"modid\", \"my_block\", new Fv2j3Block() {\n");
        docs.append("    public String id() { return \"my_block\"; }\n");
        docs.append("    public String name() { return \"My Block\"; }\n");
        docs.append("    public float hardness() { return 1.0f; }\n");
        docs.append("    public float resistance() { return 5.0f; }\n");
        docs.append("    public int harvestLevel() { return 0; }\n");
        docs.append("    public String harvestTool() { return \"pickaxe\"; }\n");
        docs.append("});\n");
        docs.append("```\n\n");

        docs.append("## Creative Tabs\n\n");
        docs.append("Create creative tabs by implementing `Fv2j3CreativeTab`:\n\n");
        docs.append("```java\n");
        docs.append("Fv2j3Registries.registerCreativeTab(\"modid\", \"my_tab\", new Fv2j3CreativeTab() {\n");
        docs.append("    public String id() { return \"my_tab\"; }\n");
        docs.append("    public String displayName() { return \"My Tab\"; }\n");
        docs.append("    public String icon() { return \"my_item\"; }\n");
        docs.append("    public int column() { return 0; }\n");
        docs.append("});\n");
        docs.append("```\n\n");

        docs.append("## Events\n\n");
        docs.append("Subscribe to events through `Fv2j3Events`:\n\n");
        docs.append("- `onClientStarting()`: Before client starts\n");
        docs.append("- `onClientStarted()`: After client starts\n");
        docs.append("- `onClientStopping()`: Before client stops\n");
        docs.append("- `onServerStarting()`: Before server starts\n");
        docs.append("- `onServerStarted()`: After server starts\n");
        docs.append("- `onServerStopping()`: Before server stops\n\n");

        docs.append("## Configuration\n\n");
        docs.append("Use `Fv2j3Config` for per-mod configuration:\n\n");
        docs.append("```java\n");
        docs.append("Fv2j3Config config = new Fv2j3Config(modId, configPath);\n");
        docs.append("config.set(\"key\", \"value\");\n");
        docs.append("config.save();\n");
        docs.append("String value = config.get(\"key\", \"default\");\n");
        docs.append("```\n\n");

        docs.append("## Logging\n\n");
        docs.append("Use `ModLogger` from `ModContext.logger()`:\n\n");
        docs.append("```java\n");
        docs.append("context.logger().info(\"Message\");\n");
        docs.append("context.logger().warn(\"Warning\");\n");
        docs.append("context.logger().error(\"Error\");\n");
        docs.append("```\n\n");

        docs.append("## Error Handling\n\n");
        docs.append("Fv2j3 provides clear error diagnostics:\n\n");
        docs.append("- Mod ID is always included\n");
        docs.append("- Phase is always identified\n");
        docs.append("- Cause chain is preserved\n");
        docs.append("- Dependency information is shown\n\n");

        docs.append("## Resources\n\n");
        docs.append("Resources are loaded from mod JARs:\n\n");
        docs.append("- Text files in `assets/modid/`\n");
        docs.append("- JSON in `assets/modid/`\n");
        docs.append("- Textures in `assets/modid/textures/`\n");
        docs.append("- Localization in `assets/modid/lang/`\n\n");

        docs.append("## Testing\n\n");
        docs.append("Run tests with:\n\n");
        docs.append("```bash\n");
        docs.append("./gradlew test\n");
        docs.append("```\n\n");
        docs.append("Run stress tests with:\n\n");
        docs.append("```bash\n");
        docs.append("./gradlew stressTest\n");
        docs.append("```\n\n");

        docs.append("## MDK\n\n");
        docs.append("The Fv2j3 MDK provides:\n\n");
        docs.append("- Gradle wrapper for building mods\n");
        docs.append("- Example mod with items, blocks, tabs\n");
        docs.append("- Metadata template\n");
        docs.append("- Resource structure\n\n");

        docs.append("Build with:\n\n");
        docs.append("```bash\n");
        docs.append("./gradlew build\n");
        docs.append("```\n\n");
        docs.append("Run Minecraft with:\n\n");
        docs.append("```bash\n");
        docs.append("./gradlew runMinecraft\n");
        docs.append("```\n\n");

        Path docFile = outputDir.resolve("FVLIF3-DOCUMENTATION.md");
        Files.writeString(docFile, docs.toString());
        System.out.println("Documentation generated: " + docFile);
    }
}
