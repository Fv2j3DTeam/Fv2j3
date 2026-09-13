package io.github.fv2j3dteam.loader.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TutorialGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TutorialGenerator <outputDir>");
            System.exit(1);
        }
        Path outputDir = Paths.get(args[0]);
        Files.createDirectories(outputDir);

        StringBuilder tutorial = new StringBuilder();
        tutorial.append("# Fv2j3 Tutorial: Building Your First Mod\n\n");
        tutorial.append("Generated: " + new java.util.Date() + "\n\n");

        tutorial.append("## Table of Contents\n\n");
        String[] sections = {
            "1. Introduction",
            "2. Installing the MDK",
            "3. Project Structure",
            "4. Running Minecraft",
            "5. Creating Your First Mod",
            "6. Mod Metadata",
            "7. Lifecycle",
            "8. Creating a Creative Tab",
            "9. Creating Items",
            "10. Creating Blocks",
            "11. Registering Content",
            "12. Resources",
            "13. Textures",
            "14. Localization",
            "15. Dependencies",
            "16. Events",
            "17. Configuration",
            "18. Building a Mod",
            "19. Installing a Built Mod",
            "20. Debugging",
            "21. Testing",
            "22. Advanced ClassLoader Concepts",
            "23. Best Practices",
            "24. Troubleshooting"
        };
        for (String s : sections) {
            tutorial.append("- ").append(s).append("\n");
        }
        tutorial.append("\n---\n\n");

        addSection(tutorial, "1. Introduction",
            "Fv2j3 is an independent Minecraft 1.12.2 mod loader. " +
            "This tutorial walks you through creating your first mod from scratch.\n\n" +
            "By the end you will have a working mod with items, blocks, and a creative tab."
        );

        addSection(tutorial, "2. Installing the MDK",
            "The Fv2j3 MDK (Mod Development Kit) is a template project.\n\n" +
            "Extract the MDK to a new directory:\n\n" +
            "```bash\n" +
            "mkdir my-mod\n" +
            "cd my-mod\n" +
            "# copy MDK files here\n" +
            "```\n\n" +
            "The MDK provides:\n" +
            "- `gradlew` and `gradlew.bat` for cross-platform builds\n" +
            "- `gradle/wrapper/` with gradle-wrapper.jar\n" +
            "- `build.gradle` configured for Fv2j3\n" +
            "- `src/main/java/` for your mod code\n" +
            "- `src/main/resources/` for metadata and resources"
        );

        addSection(tutorial, "3. Project Structure",
            "A typical MDK project structure:\n\n" +
            "```\n" +
            "my-mod/\n" +
            "├── build.gradle\n" +
            "├── settings.gradle\n" +
            "├── gradle.properties\n" +
            "├── gradlew\n" +
            "├── gradlew.bat\n" +
            "├── gradle/wrapper/\n" +
            "│   ├── gradle-wrapper.jar\n" +
            "│   └── gradle-wrapper.properties\n" +
            "└── src/\n" +
            "    ├── main/\n" +
            "    │   ├── java/com/example/mod/\n" +
            "    │   └── resources/\n" +
            "    │       └── META-INF/fv2j3.mod.json\n" +
            "    └── test/\n" +
            "```\n\n" +
            "The `src/main/java/` directory contains your Java source files.\n" +
            "The `src/main/resources/` directory contains metadata and assets."
        );

        addSection(tutorial, "4. Running Minecraft",
            "Before writing your mod, you can verify the MDK works:\n\n" +
            "```bash\n" +
            "./gradlew runMinecraft\n" +
            "```\n\n" +
            "On Windows:\n\n" +
            "```bat\n" +
            "gradlew.bat runMinecraft\n" +
            "```\n\n" +
            "This launches Minecraft 1.12.2 with the Fv2j3 loader."
        );

        addSection(tutorial, "5. Creating Your First Mod",
            "Create your mod's main class:\n\n" +
            "```java\n" +
            "package com.example.mymod;\n\n" +
            "import io.github.fv2j3dteam.api.*;\n\n" +
            "public final class MyMod implements Mod {\n" +
            "    private static final ModDescriptor DESCRIPTOR = new ModDescriptor(\n" +
            "        \"my_mod\",\n" +
            "        \"1.0.0\",\n" +
            "        \"My First Mod\",\n" +
            "        \"A tutorial mod.\",\n" +
            "        java.util.List.of(\"YourName\"),\n" +
            "        \"MIT\",\n" +
            "        java.util.List.of(),\n" +
            "        java.util.List.of(),\n" +
            "        \">=0.1.0\",\n" +
            "        \"com.example.mymod.MyMod\"\n" +
            "    );\n\n" +
            "    @Override\n" +
            "    public ModDescriptor descriptor() { return DESCRIPTOR; }\n\n" +
            "    @Override\n" +
            "    public void onLoad(ModContext context) {\n" +
            "        context.logger().info(\"MyMod loaded!\");\n" +
            "    }\n" +
            "}\n" +
            "```"
        );

        addSection(tutorial, "6. Mod Metadata",
            "Create `src/main/resources/META-INF/fv2j3.mod.json`:\n\n" +
            "```json\n" +
            "{\n" +
            "  \"modId\": \"my_mod\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"name\": \"My First Mod\",\n" +
            "  \"description\": \"A tutorial mod.\",\n" +
            "  \"authors\": [\"YourName\"],\n" +
            "  \"license\": \"MIT\",\n" +
            "  \"dependencies\": [],\n" +
            "  \"optionalDependencies\": [],\n" +
            "  \"entrypoint\": \"com.example.mymod.MyMod\"\n" +
            "}\n" +
            "```\n\n" +
            "Rules for mod IDs:\n" +
            "- Must be lowercase\n" +
            "- May contain letters, digits, underscores\n" +
            "- No whitespace or special characters\n" +
            "- Must be unique"
        );

        addSection(tutorial, "7. Lifecycle",
            "Fv2j3 mod lifecycle:\n\n" +
            "1. **onLoad()**: Called first, when mod is discovered and validated\n" +
            "2. **onInitialize()**: Called when the loader initializes\n" +
            "3. **onStart()**: Called when mod is started\n" +
            "4. **onStop()**: Called when mod is stopped\n\n" +
            "Each phase is called exactly once. Failures are reported with phase information."
        );

        addSection(tutorial, "8. Creating a Creative Tab",
            "```java\n" +
            "public final class MyTab implements Fv2j3CreativeTab {\n" +
            "    @Override public String id() { return \"my_tab\"; }\n" +
            "    @Override public String displayName() { return \"My Tab\"; }\n" +
            "    @Override public String icon() { return \"my_item\"; }\n" +
            "    @Override public int column() { return 0; }\n" +
            "}\n" +
            "```\n\n" +
            "Register in your mod's onLoad():\n\n" +
            "```java\n" +
            "Fv2j3Registries.registerCreativeTab(\"my_mod\", \"my_tab\", new MyTab());\n" +
            "```"
        );

        addSection(tutorial, "9. Creating Items",
            "```java\n" +
            "public final class MyItem implements Fv2j3Item {\n" +
            "    @Override public String id() { return \"my_item\"; }\n" +
            "    @Override public String name() { return \"My Item\"; }\n" +
            "    @Override public int maxStackSize() { return 64; }\n" +
            "}\n" +
            "```\n\n" +
            "Register:\n\n" +
            "```java\n" +
            "Fv2j3Registries.registerItem(\"my_mod\", \"my_item\", new MyItem());\n" +
            "```"
        );

        addSection(tutorial, "10. Creating Blocks",
            "```java\n" +
            "public final class MyBlock implements Fv2j3Block {\n" +
            "    @Override public String id() { return \"my_block\"; }\n" +
            "    @Override public String name() { return \"My Block\"; }\n" +
            "    @Override public float hardness() { return 1.0f; }\n" +
            "    @Override public float resistance() { return 5.0f; }\n" +
            "    @Override public int harvestLevel() { return 0; }\n" +
            "    @Override public String harvestTool() { return \"pickaxe\"; }\n" +
            "}\n" +
            "```\n\n" +
            "Register:\n\n" +
            "```java\n" +
            "Fv2j3Registries.registerBlock(\"my_mod\", \"my_block\", new MyBlock());\n" +
            "```"
        );

        addSection(tutorial, "11. Registering Content",
            "Register all content in your mod's onLoad() method:\n\n" +
            "```java\n" +
            "@Override\n" +
            "public void onLoad(ModContext context) {\n" +
            "    Fv2j3Registries.registerItem(\"my_mod\", \"my_item\", new MyItem());\n" +
            "    Fv2j3Registries.registerBlock(\"my_mod\", \"my_block\", new MyBlock());\n" +
            "    Fv2j3Registries.registerCreativeTab(\"my_mod\", \"my_tab\", new MyTab());\n" +
            "    context.logger().info(\"MyMod registered content.\");\n" +
            "}\n" +
            "```"
        );

        addSection(tutorial, "12. Resources",
            "Resources are placed in `src/main/resources/`:\n\n" +
            "```\n" +
            "src/main/resources/\n" +
            "├── META-INF/\n" +
            "│   └── fv2j3.mod.json\n" +
            "└── assets/\n" +
            "    └── my_mod/\n" +
            "        ├── lang/\n" +
            "        │   └── en_us.lang\n" +
            "        └── textures/\n" +
            "            ├── items/\n" +
            "            │   └── my_item.png\n" +
            "            └── blocks/\n" +
            "                └── my_block.png\n" +
            "```"
        );

        addSection(tutorial, "13. Textures",
            "Textures must be valid PNG files (16x16 or higher resolution).\n\n" +
            "Save your textures as:\n" +
            "- `assets/my_mod/textures/items/my_item.png`\n" +
            "- `assets/my_mod/textures/blocks/my_block.png`\n\n" +
            "Fv2j3 will load these textures into Minecraft's resource system."
        );

        addSection(tutorial, "14. Localization",
            "Create `assets/my_mod/lang/en_us.lang`:\n\n" +
            "```\n" +
            "item.my_mod.my_item.name=My Item\n" +
            "tile.my_mod.my_block.name=My Block\n" +
            "```\n\n" +
            "This translates your items and blocks for English (en_us) locale."
        );

        addSection(tutorial, "15. Dependencies",
            "To depend on another mod, add to metadata:\n\n" +
            "```json\n" +
            "{\n" +
            "  \"dependencies\": [\n" +
            "    {\"modId\": \"other_mod\", \"version\": \">=1.0.0\", \"type\": \"REQUIRED\"}\n" +
            "  ]\n" +
            "}\n" +
            "```\n\n" +
            "Version constraints:\n" +
            "- `>=1.0.0`: Greater than or equal to 1.0.0\n" +
            "- `<2.0.0`: Less than 2.0.0\n" +
            "- `>=1.0.0 <2.0.0`: Range constraint\n\n" +
            "Dependency types:\n" +
            "- `REQUIRED`: Mod won't load without it\n" +
            "- `OPTIONAL`: Mod loads without it, but uses it if present"
        );

        addSection(tutorial, "16. Events",
            "Register event listeners:\n\n" +
            "```java\n" +
            "@Override\n" +
            "public void onInitialize(ModContext context) {\n" +
            "    Fv2j3Events.onClientStarted(e -> {\n" +
            "        context.logger().info(\"Client started!\");\n" +
            "    });\n" +
            "}\n" +
            "```\n\n" +
            "Available events:\n" +
            "- onClientStarting / onClientStarted / onClientStopping\n" +
            "- onServerStarting / onServerStarted / onServerStopping\n" +
            "- onRegistryBuild"
        );

        addSection(tutorial, "17. Configuration",
            "Use `Fv2j3Config` for per-mod configuration:\n\n" +
            "```java\n" +
            "@Override\n" +
            "public void onLoad(ModContext context) {\n" +
            "    java.nio.file.Path configFile = context.attribute(\"configDir\", java.nio.file.Path.class)\n" +
            "        .resolve(\"my_mod.cfg\");\n" +
            "    Fv2j3Config config = new Fv2j3Config(\"my_mod\", configFile);\n" +
            "    config.setBool(\"enabled\", true);\n" +
            "    config.setInt(\"value\", 42);\n" +
            "    config.save();\n" +
            "}\n" +
            "```"
        );

        addSection(tutorial, "18. Building a Mod",
            "Build your mod JAR:\n\n" +
            "```bash\n" +
            "./gradlew build\n" +
            "```\n\n" +
            "The resulting JAR is in `build/libs/`."
        );

        addSection(tutorial, "19. Installing a Built Mod",
            "Copy the built JAR to your Fv2j3 mods directory:\n\n" +
            "```bash\n" +
            "cp build/libs/my_mod-1.0.0.jar ~/.fv2j3/mods/\n" +
            "```\n\n" +
            "Or use the loader's mods directory."
        );

        addSection(tutorial, "20. Debugging",
            "Common issues:\n\n" +
            "- **Mod not loading**: Check `META-INF/fv2j3.mod.json` is present\n" +
            "- **Class not found**: Verify entrypoint in metadata\n" +
            "- **Duplicate ID**: Ensure all item/block IDs are unique\n" +
            "- **Texture not appearing**: Verify texture path matches mod ID\n\n" +
            "Use `./gradlew --info` for more detailed output."
        );

        addSection(tutorial, "21. Testing",
            "Run unit tests:\n\n" +
            "```bash\n" +
            "./gradlew test\n" +
            "```\n\n" +
            "Run the stress test:\n\n" +
            "```bash\n" +
            "./gradlew stressTest\n" +
            "```\n\n" +
            "Run real Minecraft:\n\n" +
            "```bash\n" +
            "./gradlew runMinecraft\n" +
            "```"
        );

        addSection(tutorial, "22. Advanced ClassLoader Concepts",
            "Each mod gets its own ClassLoader with controlled visibility.\n\n" +
            "ClassLoader hierarchy:\n" +
            "```\n" +
            "System ClassLoader\n" +
            "  └── Fv2j3 API\n" +
            "      └── Minecraft Classes\n" +
            "          └── ModClassLoader\n" +
            "              └── Mod\n" +
            "```\n\n" +
            "Mods cannot directly see other mods' classes unless they are declared dependencies."
        );

        addSection(tutorial, "23. Best Practices",
            "1. Always use unique mod IDs with proper namespace\n" +
            "2. Register content in onLoad() for early initialization\n" +
            "3. Use the logger for debugging\n" +
            "4. Validate dependencies in metadata\n" +
            "5. Provide textures for all items and blocks\n" +
            "6. Use the event system for cross-mod interaction\n" +
            "7. Save configuration in the standard config directory"
        );

        addSection(tutorial, "24. Troubleshooting",
            "**Issue**: \"Mod not found in metadata\"\n" +
            "**Solution**: Check that META-INF/fv2j3.mod.json exists and is valid JSON.\n\n" +
            "**Issue**: \"Duplicate registration\"\n" +
            "**Solution**: Ensure your item/block IDs are unique within the mod.\n\n" +
            "**Issue**: \"Class not found\"\n" +
            "**Solution**: Verify your entrypoint matches the class name.\n\n" +
            "**Issue**: \"Dependency not found\"\n" +
            "**Solution**: Check that the dependency mod is also installed.\n\n" +
            "For more help, run `./gradlew --info` to see detailed logging."
        );

        Path tutorialFile = outputDir.resolve("FVLIF3-TUTORIAL.md");
        Files.writeString(tutorialFile, tutorial.toString());
        System.out.println("Tutorial generated: " + tutorialFile);
    }

    private static void addSection(StringBuilder sb, String title, String content) {
        sb.append("## ").append(title).append("\n\n");
        sb.append(content).append("\n\n");
    }
}
