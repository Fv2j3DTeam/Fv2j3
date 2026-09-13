package io.github.fv2j3dteam.loader.runtime;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public final class StressModGenerator {
    private static final int MOD_COUNT = 310;
    private static final int ITEMS_PER_MOD = 5;
    private static final int BLOCKS_PER_MOD = 5;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: StressModGenerator <outputDir> <jarsDir> <loaderApiJar> [count]");
            System.exit(1);
        }
        Path outputDir = Paths.get(args[0]);
        Path jarsDir = Paths.get(args[1]);
        Path loaderApiJar = Paths.get(args[2]);
        int count = args.length >= 4 ? Math.max(1, Integer.parseInt(args[3])) : MOD_COUNT;
        generate(outputDir, jarsDir, loaderApiJar, count);
    }

    /**
     * Generates {@code count} stress mods (each with 5 items, 5 blocks,
     * 1 creative tab) into {@code jarsDir}, with the generated source
     * code in {@code outputDir}. The loader-api JAR is needed on the
     * compiler classpath.
     */
    public static Summary generate(Path outputDir, Path jarsDir, Path loaderApiJar, int count) throws Exception {
        if (!Files.exists(loaderApiJar)) {
            throw new IllegalArgumentException("loader-api JAR not found: " + loaderApiJar);
        }
        List<Path> classpath = List.of(loaderApiJar);
        Files.createDirectories(outputDir);
        Files.createDirectories(jarsDir);

        int items = 0, blocks = 0, tabs = 0;

        for (int i = 1; i <= count; i++) {
            String modId = String.format("testmod%03d", i);
            Path modDir = outputDir.resolve(modId);
            Files.createDirectories(modDir);
            Files.createDirectories(modDir.resolve("src/main/java/com/stressmod" + i));
            Files.createDirectories(modDir.resolve("src/main/resources/META-INF"));

            String category = categorize(i);
            generateMod(modDir, modId, i, category);

            List<String> deps = generateDependencies(i);
            String jarName = modId + ".jar";
            Path jarPath = jarsDir.resolve(jarName);
            createModJar(modDir, jarPath, modId, i, deps, classpath);

            if (!category.equals("failure")) {
                items += ITEMS_PER_MOD;
                blocks += BLOCKS_PER_MOD;
                tabs++;
            }

            if (i % 50 == 0) {
                System.out.println("Generated " + i + " test mods...");
            }
        }

        Path summary = jarsDir.resolve("SUMMARY.txt");
        String summaryText = String.format(
            "Fv2j3 Stress Test Summary\n" +
            "=========================\n" +
            "Total Mods: %d\n" +
            "Creative Tabs: %d\n" +
            "Items: %d\n" +
            "Blocks: %d\n" +
            "Location: %s\n",
            count, tabs, items, blocks, jarsDir.toAbsolutePath()
        );
        Files.writeString(summary, summaryText);
        System.out.println(summaryText);
        return new Summary(count, items, blocks, tabs, jarsDir);
    }

    /** Result of a stress generation run. */
    public record Summary(int mods, int items, int blocks, int creativeTabs, Path jarsDir) { }

    private static String categorize(int i) {
        if (i <= 200) return "basic";
        if (i <= 230) return "resource";
        if (i <= 250) return "event";
        if (i <= 270) return "config";
        if (i <= 290) return "dependency";
        if (i <= 300) return "large";
        return "failure";
    }

    private static List<String> generateDependencies(int i) {
        List<String> deps = new ArrayList<>();
        if (i > 291 && i <= 300) {
            int base = ((i - 292) % 10) + 1;
            deps.add(String.format("testmod%03d", base));
        }
        return deps;
    }

    private static void generateMod(Path modDir, String modId, int index, String category) throws Exception {
        String className = "com.stressmod" + index + ".StressMod" + index;
        String packageName = "com.stressmod" + index;

        String modJava = generateModJava(className, modId, index, category);
        Files.writeString(modDir.resolve("src/main/java/" + className.replace('.', '/') + ".java"), modJava);

        for (int j = 1; j <= ITEMS_PER_MOD; j++) {
            String itemName = "StressItem" + index + "_" + j;
            String itemClass = "com.stressmod" + index + "." + itemName;
            String itemJava = String.format(
                "package %s;\n\n" +
                "import io.github.fv2j3dteam.api.Fv2j3Item;\n\n" +
                "public final class %s implements Fv2j3Item {\n" +
                "    private final int itemIndex;\n" +
                "    public %s(int idx) { this.itemIndex = idx; }\n" +
                "    @Override public String id() { return \"stress_item_\" + itemIndex; }\n" +
                "    @Override public String name() { return \"Test Item \" + itemIndex + \" (\" + \"%s\" + \")\"; }\n" +
                "    @Override public int maxStackSize() { return 64; }\n" +
                "}",
                packageName(itemClass), itemName, itemName, modId
            );
            Files.writeString(modDir.resolve("src/main/java/" + itemClass.replace('.', '/') + ".java"), itemJava);
        }

        for (int j = 1; j <= BLOCKS_PER_MOD; j++) {
            String blockName = "StressBlock" + index + "_" + j;
            String blockClass = "com.stressmod" + index + "." + blockName;
            String blockJava = String.format(
                "package %s;\n\n" +
                "import io.github.fv2j3dteam.api.Fv2j3Block;\n\n" +
                "public final class %s implements Fv2j3Block {\n" +
                "    private final int blockIndex;\n" +
                "    public %s(int idx) { this.blockIndex = idx; }\n" +
                "    @Override public String id() { return \"stress_block_\" + blockIndex; }\n" +
                "    @Override public String name() { return \"Test Block \" + blockIndex + \" (\" + \"%s\" + \")\"; }\n" +
                "    @Override public float hardness() { return 1.0f; }\n" +
                "    @Override public float resistance() { return 5.0f; }\n" +
                "    @Override public int harvestLevel() { return 0; }\n" +
                "    @Override public String harvestTool() { return \"pickaxe\"; }\n" +
                "}",
                packageName(blockClass), blockName, blockName, modId
            );
            Files.writeString(modDir.resolve("src/main/java/" + blockClass.replace('.', '/') + ".java"), blockJava);
        }

        if (category.equals("basic") || category.equals("large")) {
            String tabClass = "com.stressmod" + index + ".StressTab" + index;
            String tabJava = generateCreativeTabJava(tabClass, modId, index);
            Files.writeString(modDir.resolve("src/main/java/" + tabClass.replace('.', '/') + ".java"), tabJava);
        }

        if (category.equals("event")) {
            String eventClass = "com.stressmod" + index + ".EventHandler" + index;
            String eventJava = generateEventHandlerJava(eventClass, modId, index);
            Files.writeString(modDir.resolve("src/main/java/" + eventClass.replace('.', '/') + ".java"), eventJava);
        }

        if (category.equals("config")) {
            String configClass = "com.stressmod" + index + ".ConfigHandler" + index;
            String configJava = generateConfigJava(configClass, modId, index);
            Files.writeString(modDir.resolve("src/main/java/" + configClass.replace('.', '/') + ".java"), configJava);
        }

        String metadata = generateMetadata(modId, index, category);
        Files.writeString(modDir.resolve("src/main/resources/META-INF/fv2j3.mod.json"), metadata);

        Path langDir = modDir.resolve("src/main/resources/assets/" + modId + "/lang");
        Files.createDirectories(langDir);
        StringBuilder en_us = new StringBuilder();
        en_us.append("item.").append(modId).append(".name=Test Item\n");
        en_us.append("tile.").append(modId).append(".name=Test Block\n");
        Files.writeString(langDir.resolve("en_us.lang"), en_us.toString());
    }

    private static String generateModJava(String className, String modId, int index, String category) {
        StringBuilder itemRegs = new StringBuilder();
        for (int j = 1; j <= ITEMS_PER_MOD; j++) {
            itemRegs.append("        Fv2j3Registries.registerItem(\"" + modId + "\", \"stress_item_" + j + "\", new StressItem" + index + "_" + j + "(" + j + "));\n");
        }
        StringBuilder blockRegs = new StringBuilder();
        for (int j = 1; j <= BLOCKS_PER_MOD; j++) {
            blockRegs.append("        Fv2j3Registries.registerBlock(\"" + modId + "\", \"stress_block_" + j + "\", new StressBlock" + index + "_" + j + "(" + j + "));\n");
        }
        String depList = generateDependencyList(category, index);
        boolean includeTab = category.equals("basic") || category.equals("large");
        String tabReg = includeTab
                ? "        Fv2j3Registries.registerCreativeTab(\"" + modId + "\", \"stress_tab\", new StressTab" + index + "());\n"
                : "";
        return
            "package " + packageName(className) + ";\n\n" +
            "import io.github.fv2j3dteam.api.*;\n\n" +
            "public final class StressMod" + index + " implements Mod {\n" +
            "    private static final ModDescriptor DESCRIPTOR = new ModDescriptor(\n" +
            "        \"" + modId + "\", \"" + index + ".0.0\", \"Stress Test Mod " + index + "\",\n" +
            "        \"Generated stress test mod for Fv2j3 testing. Category: " + category + ".\",\n" +
            "        java.util.List.of(\"Fv2j3 Test Suite\"), \"MIT\",\n" +
            "        " + depList + ",\n" +
            "        java.util.List.of(),\n" +
            "        \">=0.1.0\", \"" + className + "\"\n" +
            "    );\n\n" +
            "    @Override public ModDescriptor descriptor() { return DESCRIPTOR; }\n\n" +
            "    @Override\n" +
            "    public void onLoad(ModContext context) {\n" +
            "        context.logger().info(\"StressMod" + index + " loaded (category: " + category + ").\");\n" +
            itemRegs +
            blockRegs +
            tabReg +
            "    }\n\n" +
            "    @Override\n" +
            "    public void onInitialize(ModContext context) {\n" +
            "        context.logger().info(\"StressMod" + index + " initialized.\");\n" +
            "    }\n\n" +
            "    @Override\n" +
            "    public void onStart(ModContext context) {\n" +
            "        context.logger().info(\"StressMod" + index + " started.\");\n" +
            "    }\n\n" +
            "    @Override\n" +
            "    public void onStop(ModContext context) {\n" +
            "        context.logger().info(\"StressMod" + index + " stopped.\");\n" +
            "    }\n" +
            "}";
    }

    private static String packageName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(0, lastDot) : "";
    }

    private static String generateDependencyList(String category, int index) {
        if (category.equals("dependency") && index > 291) {
            int base = ((index - 292) % 10) + 1;
            return String.format(
                "java.util.List.of(new ModDependency(\"testmod%03d\", \">=1.0.0\", DependencyType.REQUIRED))",
                base
            );
        }
        return "java.util.List.of()";
    }

    private static String generateItemJava(String className, String modId, int index, int itemIndex) {
        return String.format(
            "package %s;\n\n" +
            "import io.github.fv2j3dteam.api.Fv2j3Item;\n\n" +
            "public final class StressItem%d implements Fv2j3Item {\n" +
            "    private final int itemIndex;\n" +
            "    public StressItem%d(int idx) { this.itemIndex = idx; }\n" +
            "    @Override public String id() { return \"stress_item_\" + itemIndex; }\n" +
            "    @Override public String name() { return \"Test Item \" + itemIndex + \" (%s)\"; }\n" +
            "    @Override public int maxStackSize() { return 64; }\n" +
            "}",
            packageName(className), itemIndex, itemIndex, modId
        );
    }

    private static String generateBlockJava(String className, String modId, int index, int blockIndex) {
        return String.format(
            "package %s;\n\n" +
            "import io.github.fv2j3dteam.api.Fv2j3Block;\n\n" +
            "public final class StressBlock%d implements Fv2j3Block {\n" +
            "    private final int blockIndex;\n" +
            "    public StressBlock%d(int idx) { this.blockIndex = idx; }\n" +
            "    @Override public String id() { return \"stress_block_\" + blockIndex; }\n" +
            "    @Override public String name() { return \"Test Block \" + blockIndex + \" (%s)\"; }\n" +
            "    @Override public float hardness() { return 1.0f; }\n" +
            "    @Override public float resistance() { return 5.0f; }\n" +
            "    @Override public int harvestLevel() { return 0; }\n" +
            "    @Override public String harvestTool() { return \"pickaxe\"; }\n" +
            "}",
            packageName(className), blockIndex, blockIndex, modId
        );
    }

    private static String generateCreativeTabJava(String className, String modId, int index) {
        String pkg = packageName(className);
        String displayName = "Stress Test Mod " + index;
        return "package " + pkg + ";\n\n" +
            "import io.github.fv2j3dteam.api.Fv2j3CreativeTab;\n\n" +
            "public final class StressTab" + index + " implements Fv2j3CreativeTab {\n" +
            "    @Override public String id() { return \"stress_tab\"; }\n" +
            "    @Override public String displayName() { return \"" + displayName + "\"; }\n" +
            "    @Override public String icon() { return \"stress_item_1\"; }\n" +
            "    @Override public int column() { return 0; }\n" +
            "}\n";
    }

    private static String generateEventHandlerJava(String className, String modId, int index) {
        String pkg = packageName(className);
        return "package " + pkg + ";\n\n" +
            "import io.github.fv2j3dteam.api.*;\n\n" +
            "public final class EventHandler" + index + " {\n" +
            "    public void onClientStarting(Fv2j3Event e) {\n" +
            "        System.out.println(\"EventHandler" + index + ": client starting\");\n" +
            "    }\n" +
            "}\n";
    }

    private static String generateConfigJava(String className, String modId, int index) {
        String pkg = packageName(className);
        return "package " + pkg + ";\n\n" +
            "import io.github.fv2j3dteam.api.Fv2j3Config;\n\n" +
            "public final class ConfigHandler" + index + " {\n" +
            "    private static Fv2j3Config config;\n" +
            "    public static void init(String modId, java.nio.file.Path configDir) {\n" +
            "        java.nio.file.Path configFile = configDir.resolve(\"" + modId + ".cfg\");\n" +
            "        config = new Fv2j3Config(modId, configFile);\n" +
            "        config.set(\"enabled\", \"true\");\n" +
            "        config.setInt(\"value\", " + index + ");\n" +
            "    }\n" +
            "    public static Fv2j3Config getConfig() { return config; }\n" +
            "}\n";
    }

    private static String generateMetadata(String modId, int index, String category) {
        boolean hasTab = category.equals("basic") || category.equals("large");
        if (category.equals("dependency") && index > 291) {
            int base = ((index - 292) % 10) + 1;
            return String.format("""
                {
                    "id": "testmod%03d",
                    "version": "%d.0.0",
                    "name": "Stress Test Mod %d",
                    "description": "Generated stress test mod. Category: %s.",
                    "authors": ["Fv2j3 Test Suite"],
                    "license": "MIT",
                    "dependencies": [{"id": "testmod%03d", "versionRange": ">=1.0.0", "type": "REQUIRED"}],
                    "optionalDependencies": [],
                    "entrypoint": "com.stressmod%d.StressMod%d"
                }
                """, index, index, index, category, base, index, index);
        }
        return String.format("""
            {
                "id": "%s",
                "version": "%d.0.0",
                "name": "Stress Test Mod %d",
                "description": "Generated stress test mod. Category: %s.",
                "authors": ["Fv2j3 Test Suite"],
                "license": "MIT",
                "dependencies": [],
                "optionalDependencies": [],
                "entrypoint": "com.stressmod%d.StressMod%d"
            }
            """, modId, index, index, category, index, index);
    }

    private static String defaultEntrypoint(String modId) {
        return modId.replace('-', '_') + ".StressMod" + modId.replace("testmod", "");
    }

    private static void createModJar(Path sourceDir, Path jarPath, String modId, int index, List<String> deps, List<Path> classpath) throws Exception {
        Path buildDir = sourceDir.resolve("build");
        Files.createDirectories(buildDir);
            compileJavaSources(sourceDir.resolve("src/main/java"), buildDir, modId, classpath);
        try (JarOutputStream jos = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath)))) {
            jos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0\nFv2j3-Mod: true\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            addToJar(jos, "META-INF/fv2j3.mod.json",
                "src/main/resources/META-INF/fv2j3.mod.json", sourceDir);
            addAllClassFiles(jos, buildDir);
            addAllResources(jos, sourceDir, "src/main/resources");
        }
    }

    private static void compileJavaSources(Path srcDir, Path buildDir, String modId, List<Path> classpath) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No JavaCompiler available. The JDK (not JRE) is required to compile mod sources.");
        }
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(buildDir.toFile()));
        if (!classpath.isEmpty()) {
            fileManager.setLocation(StandardLocation.CLASS_PATH, classpath.stream().map(Path::toFile).toList());
        }
        String javaVersion = System.getProperty("java.version", "");
        int majorVersion = parseMajorVersion(javaVersion);
        List<String> options = List.of("--release", String.valueOf(majorVersion));
        List<JavaFileObject> sources = new ArrayList<>();
        Files.walk(srcDir).filter(p -> p.toString().endsWith(".java")).forEach(p -> {
            sources.add(new JavaSourceFromPath(p));
        });
        boolean success = compiler.getTask(null, fileManager, null, options, null, sources).call();
        fileManager.close();
        if (!success) {
            throw new IllegalStateException("Failed to compile mod sources for " + modId);
        }
    }

    private static int parseMajorVersion(String javaVersion) {
        if (javaVersion.isEmpty()) return 26;
        String[] parts = javaVersion.split("[.\\-]");
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num >= 9 && num <= 99) return num;
            } catch (NumberFormatException ignored) {}
        }
        return 26;
    }

    private static final class JavaSourceFromPath extends SimpleJavaFileObject {
        private final Path sourcePath;
        JavaSourceFromPath(Path sourcePath) {
            super(sourcePath.toUri(), JavaFileObject.Kind.SOURCE);
            this.sourcePath = sourcePath;
        }
        @Override public CharSequence getCharContent(boolean ignore) throws IOException {
            return Files.readString(sourcePath, StandardCharsets.UTF_8);
        }
    }

    private static void addAllClassFiles(JarOutputStream jos, Path buildDir) throws Exception {
        if (!Files.exists(buildDir)) return;
        Files.walk(buildDir).forEach(file -> {
            if (file.toString().endsWith(".class") && Files.isRegularFile(file)) {
                try {
                    String relPath = buildDir.relativize(file).toString().replace(File.separatorChar, '/');
                    jos.putNextEntry(new ZipEntry(relPath));
                    jos.write(Files.readAllBytes(file));
                    jos.closeEntry();
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        });
    }

    private static void addToJar(JarOutputStream jos, String jarEntry, String sourcePath, Path sourceDir) throws Exception {
        jos.putNextEntry(new ZipEntry(jarEntry));
        Path fullPath = sourceDir.resolve(sourcePath);
        if (Files.exists(fullPath)) {
            jos.write(Files.readAllBytes(fullPath));
        }
        jos.closeEntry();
    }

    private static void addToJar(JarOutputStream jos, String jarEntry, String content) throws Exception {
        jos.putNextEntry(new ZipEntry(jarEntry));
        jos.write(content.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }

    private static void addAllResources(JarOutputStream jos, Path sourceDir, String prefix) throws Exception {
        Path srcPath = sourceDir.resolve(prefix);
        if (!Files.exists(srcPath)) return;
        Files.walk(srcPath).forEach(file -> {
            if (Files.isRegularFile(file)) {
                String relPath = srcPath.relativize(file).toString().replace(File.separatorChar, '/');
                if (relPath.contains("META-INF/fv2j3.mod.json")) return;
                try {
                    jos.putNextEntry(new ZipEntry(relPath));
                    jos.write(Files.readAllBytes(file));
                    jos.closeEntry();
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        });
    }
}
