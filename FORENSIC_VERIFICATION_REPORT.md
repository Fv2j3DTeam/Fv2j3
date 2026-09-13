# Fv2J3 FORENSIC VERIFICATION REPORT

**Date**: August 30, 2026  
**Auditor**: Independent Forensic Audit  
**Status**: **PREVIOUS PASS VERDICT REJECTED**

---

## EXECUTIVE SUMMARY

The previous "PASS" verdict is **UNSUPPORTED**. Fv2j3 is **NOT** a production-quality Minecraft 1.12.2 Mod Loader.

### Critical Findings

1. **StressModGenerator produces UNLOADABLE JARs** - Generated .class files are 0 bytes
2. **StressTestRunner does NOT test external mod loading** - Uses simulated objects, not real JAR loading
3. **No Minecraft registry integration** - `Fv2j3Item`/`Fv2j3Block` are plain Java interfaces
4. **Tests do not verify production code paths** - All tests are isolated unit tests or math calculations
5. **No external mod JAR ever loaded through the real ClassLoader pipeline**

---

## PHASE 1: PROJECT INVENTORY

### Files Analyzed

| File | Purpose | Status |
|------|---------|--------|
| `Bootstrap.java` | Entry point | IMPLEMENTED |
| `Fv2j3Loader.java` | Core loader logic | IMPLEMENTED |
| `ModClassLoader.java` | Per-mod ClassLoader | IMPLEMENTED |
| `ModRegistry.java` | Mod container registry | IMPLEMENTED |
| `JarModDiscovery.java` | JAR metadata reading | IMPLEMENTED |
| `ModMetadataParser.java` | JSON parsing | IMPLEMENTED |
| `ModDependencyGraph.java` | Dependency ordering | IMPLEMENTED |
| `DefaultModRuntime.java` | Mod lifecycle | IMPLEMENTED |
| `MinecraftBootstrap.java` | MC launch | IMPLEMENTED |
| `MinecraftModMenuTransformer.java` | GUI injection | IMPLEMENTED |
| `StressModGenerator.java` | **Produces 0-byte .class files** | BROKEN |
| `StressTestRunner.java` | **Does NOT load external JARs** | INVALID |
| `Fv2j3Registries.java` | In-memory registry | NO MC INTEGRATION |
| `Fv2j3Item.java` | Item interface | NO MC INTEGRATION |
| `Fv2j3Block.java` | Block interface | NO MC INTEGRATION |
| `Fv2j3Events.java` | Static event bus | MOD ISOLATION BROKEN |
| `RegistryAndEventTest.java` | Tests Fv2j3Registries | NO MC VERIFICATION |
| `ModGuiLayoutTest.java` | Tests math calculations | NO GUI VERIFICATION |

---

## PHASE 2: MOD LOADING PIPELINE TRACE

### Verified Steps (7/18)

| Step | Class | Method | Status |
|------|-------|--------|--------|
| Minecraft launch | Bootstrap | main() | VERIFIED |
| Loader init | Fv2j3Loader | initialize() | VERIFIED |
| Mod discovery | JarModDiscovery | discover() | VERIFIED |
| Metadata parsing | ModMetadataParser | parse() | VERIFIED |
| Dependency resolution | ModDependencyGraph | resolveOrder() | VERIFIED |
| ClassLoader creation | ModClassLoader | constructor | VERIFIED |
| Class loading attempt | DefaultModRuntime | instantiateMod() | **FAILS** |

### Unverified Steps (11/18)

| Step | Reason |
|------|--------|
| Mod class loading | **Generated JARs have 0-byte .class files** |
| Mod instantiation | **Cannot instantiate from empty classes** |
| Lifecycle callbacks | **No mod can reach this step** |
| Registry operations | **No Minecraft registry involved** |
| Event registration | **Static listeners, mod isolation broken** |
| Resource loading | **Never tested** |
| Minecraft registry integration | **No actual Minecraft classes used** |
| Mod GUI | **Only marker files created** |
| Shutdown | **Never tested with real mods** |

---

## PHASE 3: EXTERNAL MOD TEST VERIFICATION

### CRITICAL BUG: StressModGenerator produces invalid JARs

**File**: `StressModGenerator.java:345-358`

```java
private static void addAllJavaSources(JarOutputStream jos, Path sourceDir, String prefix) throws Exception {
    // ...
    jos.putNextEntry(new ZipEntry("classes/" + relPath.replace(".java", ".class")));
    jos.write(new byte[0]);  // <-- EMPTY CLASS FILES!
    // ...
}
```

**Evidence**:
```bash
$ unzip -l build/stress-jars/testmod001.jar
  Length      Date   Time    Name
       0  ...   classes/com/stressmod1/StressMod1.class   # 0 BYTES!
       0  ...   classes/com/stressmod1/StressItem1.class
       0  ...   classes/com/stressmod1/StressItem2.class
       ...
```

**Impact**: 310 JARs are completely unloadable.

### CRITICAL BUG: StressTestRunner doesn't test external JAR loading

**File**: `StressTestRunner.java:37-76`

The test:
1. Reads JAR manifests (lines 37-58) - only counts JSON metadata
2. Creates "SimulatedMods" directly (lines 60-76) - **never loads external JARs**

```java
// This creates objects directly - NOT loading from JARs!
for (int i = 1; i <= 200; i++) {
    String modId = String.format("simmod%03d", i);
    Fv2j3Registries.registerCreativeTab(modId, "tab_" + i, new SimulatedTab(...));
    Fv2j3Registries.registerItem(modId, "item_" + i, new SimulatedItem(...));
    // ...
}
```

**Impact**: The stress test never exercises:
- `ModClassLoader.loadClass()`
- `DefaultModRuntime.instantiateMod()`
- Actual JAR class loading
- ClassLoader delegation hierarchy

### Stress Test Classification: **INVALID**

The stress test is useless for production-loader verification.

---

## PHASE 4: CLASSLOADER FORENSICS

### Architecture (THEORETICAL)

```
Bootstrap/System ClassLoader
    └── Fv2j3 API ClassLoader (loader-api.jar)
            └── Mod ClassLoaders (per-mod)
                    └── Minecraft ClassLoader
```

### ClassLoader Isolation: BROKEN

**Evidence**: `ModClassLoader.java:81-97`

```java
@Override
public InputStream getResource(String name) {
    for (ClassLoader dependencyLoader : dependencyLoaders) {
        // Searches dependency loaders FIRST
        URL resource = dependencyLoader.getResource(name);
        // ...
    }
    // Then parent
    URL resource = getParent() == null ? null : getParent().getResource(name);
    // ...
    return super.getResource(name);  // Last: self
}
```

**Problem**: Mod A can access Mod B's resources if B is a dependency.

### Protected Packages: PARTIAL

```java
private static final List<String> PROTECTED_PREFIXES = List.of(
    "io.github.fv2j3dteam.",
    "net.minecraft."
);
```

**Missing**: `io.github.fv2j3dteam.loader.core` is NOT protected.

### Event Bus: MOD ISOLATION BROKEN

**File**: `Fv2j3Events.java:7-14`

```java
public final class Fv2j3Events {
    private static final List<Consumer<Fv2j3Event>> CLIENT_STARTING = new ArrayList<>();
    private static final List<Consumer<Fv2j3Event>> CLIENT_STARTED = new ArrayList<>();
    // ...
}
```

**Problem**: Static lists survive across ClassLoaders. Mod A's listeners can leak to Mod B.

---

## PHASE 5: DEPENDENCY SYSTEM

### Verified: Dependency ordering works (for valid JARs)

```java
// ModDependencyGraph.resolveOrder() uses topological sort
// Validates for cycles
// Returns ordered list
```

### NOT Tested:

- Required dependency missing
- Optional dependency missing
- Dependency cycle
- Duplicate Mod ID
- Invalid metadata
- Version incompatibility

**Classification**: PARTIALLY VERIFIED (only ordering, not failure modes)

---

## PHASE 6: FAILURE INJECTION

**NOT TESTED**

No tests create mods that:
- Throw during construction
- Register duplicate IDs
- Throw from event listeners
- Have invalid metadata

**Classification**: UNVERIFIED

---

## PHASE 7: REGISTRY FORENSICS

### CRITICAL: No Minecraft Integration

**File**: `Fv2j3Item.java:3-8`

```java
public interface Fv2j3Item {
    String id();
    String name();
    int maxStackSize();
    default int durability() { return 0; }
}
```

**Problem**: This is a plain Java interface. It does NOT:
- Extend `net.minecraft.item.Item`
- Connect to Minecraft's item registry
- Appear in the creative inventory
- Have any game functionality

**Same for**: `Fv2j3Block`, `Fv2j3CreativeTab`

### What Fv2j3Registries Actually Does

```java
public final class Fv2j3Registries {
    private static final Map<String, Fv2j3Item> ITEMS = new LinkedHashMap<>();
    // ...
}
```

**Classification**: **FAILS** - No Minecraft registry integration

---

## PHASE 8: EVENT SYSTEM

### Event Bus is Broken for Mod Isolation

**File**: `Fv2j3Events.java:34-37`

```java
private static void fire(Consumer<Fv2j3Event> listener, Fv2j3Event event) {
    try { listener.accept(event); }
    catch (Throwable t) { /* log and continue */ }  // Swallows all exceptions!
}
```

**Problems**:
1. Static lists mean cross-ClassLoader listener visibility
2. All exceptions swallowed silently
3. No ordering guarantees
4. No removal mechanism

**Classification**: FAILS - Mod isolation broken

---

## PHASE 9: RESOURCE SYSTEM

**NOT TESTED**

No external mods with actual:
- Textures
- Language files
- Custom resources
- Resource pack assets

**Classification**: UNVERIFIED

---

## PHASE 10: GUI / MINECRAFT RUNTIME

### What Works

- ASM bytecode transformation
- Loading screen display
- Mods button injection
- Basic menu navigation

### What Doesn't Work

- **Actual mod list**: `MinecraftModMenuTransformer.java:56-58`
  ```java
  public static void setMods(List<String> mods) {
      MODS = mods == null ? List.of() : List.copyOf(mods);
  }
  ```
  This receives **string representations**, not actual mod data.

- **GUI rendering**: Only draws what was passed as strings
- **Mod metadata**: Displayed as `id | name | state` strings

### Verification Method: Marker Files

**File**: `MinecraftModMenuTransformer.java:524-532`

```java
private static void marker(String name) {
    Files.writeString(directory.resolve(name), "ok\n");
}
```

**Classification**: PARTIALLY VERIFIED - Basic GUI works, but:
- No actual mod loading verification
- No metadata accuracy verification
- No 300+ mods scenario tested

---

## PHASE 11: MDK

### MDK Structure: VERIFIED

```
mdk/
├── build.gradle
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties
└── src/main/java/com/example/mod/
    └── ExampleMod.java
```

### NOT Tested:
- Can external developer build with `./gradlew build`?
- Can resulting JAR be loaded by Fv2j3?
- Does ExampleMod lifecycle execute?

**Classification**: PARTIALLY VERIFIED - Structure exists, functionality untested

---

## PHASE 12: TEST QUALITY AUDIT

### RegistryAndEventTest

**What it proves**: `Fv2j3Registries` and `Fv2j3Events` internal behavior

**What it does NOT prove**:
- Minecraft registry integration
- Actual item/block registration
- Real mod lifecycle
- ClassLoader isolation

**Mocks/Fakes**: All tests use anonymous inner classes implementing interfaces
**Classification**: MISLEADING - Tests pass but don't verify Minecraft integration

### ModGuiLayoutTest

**What it proves**: Math calculations for GUI layout

**What it does NOT prove**:
- Actual button positioning in Minecraft
- Real GUI rendering
- Mouse interaction with real buttons

**Classification**: Tests pass because they test math, not Minecraft

### StressTestRunner

**What it proves**: "SimulatedMods" can register objects to in-memory maps

**What it does NOT prove**:
- External JAR loading
- ClassLoader behavior
- Minecraft integration
- Real mod lifecycle

**Classification**: INVALID - Does not test production code paths

---

## PHASE 13: BUILD TASK AUDIT

| Task | Implementation | Result |
|------|----------------|--------|
| `./gradlew test` | JUnit tests | PASSES (but tests internal APIs) |
| `./gradlew clean build` | Compiles all modules | PASSES |
| `./gradlew runMinecraft` | Launches MC with test mod | PASSES (1 mod loads) |
| `./gradlew stressTest` | Runs StressTestRunner | PASSES (but INVALID) |
| `./gradlew testMdk` | Checks file existence | PASSES (structure only) |
| `./gradlew generateDocumentation` | Writes markdown | PASSES |
| `./gradlew generateTutorial` | Writes markdown | PASSES |

---

## PHASE 14: NUMERICAL EVIDENCE

### Real Numbers

| Metric | Value | Source |
|--------|-------|--------|
| Total external Mod JARs tested | **0** | No JARs loaded |
| Successfully discovered | 1 | Only testModJar |
| Successfully metadata-parsed | 1 | testModJar.json |
| Successfully dependency-resolved | 1 | testModJar |
| Successfully ClassLoader-created | **0** | Stress JARs empty |
| Successfully instantiated | **0** | Cannot instantiate empty |
| Successfully lifecycle-initialized | **0** | Cannot reach this |
| Creative Tabs actually registered in Minecraft | **0** | No MC integration |
| Items actually registered in Minecraft | **0** | No MC integration |
| Blocks actually registered in Minecraft | **0** | No MC integration |
| Event listeners actually invoked | **0** | Never tested |
| Resources actually loaded | **0** | Never tested |
| Minecraft launches | 1 | Bootstrap |
| Minecraft launches with all stress Mods | **0** | JARs are invalid |

### Classification: ALL UNVERIFIED

---

## PHASE 15: FINAL VERDICT

### Subsystem Classifications

| Subsystem | Status | Reason |
|-----------|--------|--------|
| Bootstrap/Entry Point | VERIFIED | Code reviewed |
| State Machine | VERIFIED | Simple enum transitions |
| Mod Discovery | VERIFIED | JAR reading works |
| Metadata Parsing | VERIFIED | JSON parsing works |
| Dependency Resolution | VERIFIED | Topological sort works |
| ClassLoader Creation | VERIFIED | URLClassLoader created |
| **Mod Class Loading** | **FAILED** | Generated .class files are 0 bytes |
| **Mod Instantiation** | **FAILED** | Cannot load empty classes |
| **Lifecycle Callbacks** | **UNVERIFIED** | Never reaches this |
| **Registry Integration** | **FAILED** | No Minecraft classes |
| **Item/Block Registration** | **FAILED** | No MC integration |
| **Event System** | **FAILED** | Broken mod isolation |
| **Resource System** | **UNVERIFIED** | Never tested |
| **GUI Integration** | **PARTIALLY VERIFIED** | Basic rendering works |
| **MDK** | **PARTIALLY VERIFIED** | Structure exists |

### Critical Findings

1. **StressModGenerator writes 0-byte .class files** - Makes 310 JARs unloadable
2. **StressTestRunner doesn't load external JARs** - Simulates everything
3. **No Minecraft item/block integration** - Pure Java interfaces
4. **Event bus breaks mod isolation** - Static listeners across ClassLoaders
5. **GUI shows strings, not actual mod data** - No verification of loaded mods

### High-Risk Findings

1. No failure injection testing
2. No resource loading testing
3. No dependency failure mode testing
4. ClassLoader resource delegation is bidirectional
5. No verification that mods actually execute lifecycle code

### Medium-Risk Findings

1. Protected packages don't include `io.github.fv2j3dteam.loader.core`
2. `Fv2j3Config` never tested with real mod JARs
3. Documentation/tags are generated, not from actual execution

### Low-Risk Findings

1. Some tests are overly specific to implementation details
2. Error messages could be more descriptive

### Tests That Were Misleading

1. **RegistryAndEventTest**: Tests pass but don't verify Minecraft integration
2. **ModGuiLayoutTest**: Tests pass but test math, not Minecraft GUI
3. **StressTestRunner**: Reports "PASS" but never loads external JARs

### Features Previously Claimed Complete But Actually Unverified

1. 300+ mod stress test (INVALID)
2. Minecraft item registration (NO MC INTEGRATION)
3. Minecraft block registration (NO MC INTEGRATION)
4. Creative tab registration (NO MC INTEGRATION)
5. Event listener invocation (NEVER TESTED)
6. Resource loading (NEVER TESTED)
7. Real mod lifecycle (NEVER VERIFIED)
8. ClassLoader isolation (BROKEN)

---

## CONCLUSION

**PREVIOUS PASS VERDICT REJECTED**

Fv2j3 is a **prototype mod loader foundation** with:
- Working bootstrap and discovery
- Theoretical mod loading pipeline
- Broken stress testing
- No Minecraft game integration
- Incomplete mod isolation
- Unverified critical features

It is **NOT** a production-quality Minecraft 1.12.2 Mod Loader.

### Recommendations

1. **Fix StressModGenerator**: Actually compile Java sources, don't write empty bytes
2. **Fix StressTestRunner**: Actually load external JARs through ModClassLoader
3. **Add Minecraft integration**: Connect Fv2j3Item to net.minecraft.item.Item
4. **Fix event bus**: Make listeners ClassLoader-scoped, not static
5. **Add failure injection tests**: Create mods that intentionally fail
6. **Test resource loading**: Create mods with actual textures, lang files
7. **Verify lifecycle**: Ensure onLoad/onInitialize actually execute
8. **Test dependency failures**: Missing deps, cycles, version conflicts

---

## REPRODUCTION STEPS FOR CRITICAL BUG

### Bug: StressModGenerator produces 0-byte .class files

```bash
cd /home/mac/Documents/Fv2j3
./gradlew generateStressMods
unzip -l build/stress-jars/testmod001.jar | grep class
```

**Expected**: Real compiled .class files (thousands of bytes)
**Actual**:
```
       0  ...   classes/com/stressmod1/StressMod1.class
       0  ...   classes/com/stressmod1/StressItem1.class
```

### Bug: StressTestRunner doesn't load external JARs

```bash
cd /home/mac/Documents/Fv2j3
./gradlew stressTest
cat build/stress-report.txt
```

**Expected**: Report from actual mod loading with ClassLoader verification
**Actual**: Report from SimulatedMods directly calling Fv2j3Registries API

### Bug: No Minecraft integration

```bash
cd /home/mac/Documents/Fv2j3
grep -r "net.minecraft.item.Item" loader-api/src/
```

**Expected**: Fv2j3Item extends net.minecraft.item.Item
**Actual**: No matches found

---

**END OF FORENSIC VERIFICATION REPORT**
