# MESRGL + FV2J3 — Master Implementation Spec Traceability Audit

**Date**: 2026-09-05
**Scope**: Sections 00–45 of the MESRGL + FV2J3 master implementation prompt (software ray tracing core, GPU software RT, physics, Minecraft 1.12.2 integration, quality/systems sections).
**Method**: Every claim below was verified in this session by executing builds/tests/benchmarks on this machine. No claim is taken from prior reports.

---

## 1. VERIFIED EXECUTION EVIDENCE (this session)

| Verification | Command | Result |
|---|---|---|
| MesrGL C++ core tests | `ctest`-style run of 18 executables in `/home/mac/Documents/MesrGL/build` | **18/18 exit 0** (math, raster, raytracer, core, bvh, environment, renderer, backend selection, public-API isolation, software RT/BVH/GI/materials/starfield, stress, graphics-API isolation, gpu_scene, **new** ray_stats) |
| Graphics-API dependency audit | `test_graphics_api_isolation` | **26 regex checks passed, 0 failed** (no Vulkan/GL/D3D/Metal/CUDA tokens in the rendering core) |
| JNI bridge build | fresh CMake configure in `mesrgl-integration/native/build2` against today's `libMesrGL.a` | **BUILD OK** (previous `native/build` was configured against a stale Sept-3 static lib) |
| Bridge tests on real GPU | `./bridge_tests` | **PASSED** on AMD RADV NAVI14 (Vulkan 1.4). CPU↔GPU cross-validation after fixes: mean=**2.19**/255, p99=**42** (was 8.45/225), pct≤2/255 = 91.7% / 95.7% |
| mesrgl-universe tests | `./gradlew :mesrgl-universe:test --no-watch-fs` | **102 tests, 0 failures, 0 errors, 0 skipped** (was: suite **deadlocked** — see fix #4; previous audit claimed "95 passing" but a full run hangs the JVM) |
| mesrgl-integration Java build | `./gradlew :mesrgl-integration:build --no-watch-fs` | **BUILD SUCCESSFUL** |
| Benchmark | `test_benchmark` before vs after, quiet machine | high-SPP rows **~3.9× faster** (722→151 ms, 7060→1819 ms); resolution rows up to ~2.4× faster; no regression observed. Desktop machine — treat absolute numbers as ±30 %. |

Environment notes discovered this session:
- In this sandbox, `cmake`/`make`/`g++` need `env -u CMAKE_ROOT` (a set-but-empty `CMAKE_ROOT` breaks cmake module resolution) and compilers must be invoked via `make`/`cmake --build`, not directly (sandbox `posix_spawnp` restriction).
- Gradle needs `--no-watch-fs` in this environment (rubygrapefruit `listFileSystems` NPE under the sandbox).
- `mesrgl-integration/native/build` (old) links `MesrGL/build_verify/libMesrGL.a` from Sept 3; `native/build2` now links today's `build/libMesrGL.a`. Use `build2`.

---

## 2. CRITICAL FIXES MADE THIS SESSION (all with regression tests)

### Fix 1 — BVH traversal truncated all hits closer than 1 world unit to a miss (CRITICAL, correctness)
`MesrGL/Core.cpp`: `BVH::recursiveIntersect` and `BVH::recursiveIntersectSIMD` returned **`int`** while `return bestT;` carries the hit distance. Every hit with `t < 1.0` truncated to `0`, and traversal treats `0` as a miss. Any geometry nearer than 1 unit was invisible; nearest-hit selection across children compared truncated integers (wrong child returned when both distances shared an integer bucket, e.g. 1.2 vs 1.8).
**Fix**: both functions now return `float` (header documents the invariant).
**Regression test**: `tests/test_ray_stats.cpp` "BVH sub-unit hit distance regression" (t=0.25 must hit; t=1.2 must beat t=1.8).
**Impact**: explains a large share of the CPU↔GPU cross-validation gap (kernel always used floats): mean error dropped 8.45 → 2.19 /255 (−74 %), p99 225 → 42.

### Fix 2 — Unbiased Russian roulette in the path tracer (CPU + GPU kernel, correctness)
`MesrGL/Renderer.cpp` + `MesrGL/Kernels/pathtrace.comp`: the old scheme **deterministically killed** paths when `max(albedo) < rrThreshold` (no compensation — biased dark: indirect GI = 0 for dark materials) and applied a `1/p` weight **without a matching stochastic kill** otherwise (biased bright: indirect GI inflated by up to 1/p, e.g. +25 % at albedo 0.8). Both sides were consistently wrong.
**Fix**: stochastic RR — kill with probability `1−p`, weight survivors `1/p` (p floored at the configured threshold; kill draw precedes the hemisphere draws in the shared RNG stream, mirrored exactly in the kernel for bit-parity).
**Regression test**: white-furnace test (`test_ray_stats.cpp`): diffuse object in a closed uniformly-emissive room (L=1) must render at radiance `a·L`. Results: a=0.25 → 0.2471, a=0.02 → 0.0196, a=1.0 → 1.0000 (pre-fix: ~0.5 / ~0 / —).

### Fix 3 — Shadow rays ignored `tMax` (over-occlusion + wasted traversal)
`MesrGL/Renderer.cpp::traceShadow` performed a full **nearest-hit** BVH query and ignored `ray.tMax`: an occluder **beyond** a point/spot light shadowed the surface (wrong), and no early exit. The GPU kernel already used `bvhIntersectAny(ray, tMax)`.
**Fix**: `traceShadow` = `m_bvh.intersectAny(ray, ray.tMax)`.
**Regression tests**: occluder beyond a point light does not shadow; occluder between light and surface shadows; directional-light occluder still blocks (`test_ray_stats.cpp`).

### Fix 4 — Deadlock + data race in `mesrgl-universe` SmokeSystem (CRITICAL, threading)
`mesrgl-universe/.../smoke/SmokeSystem.java::step()` held the **read** lock while calling `getOrCreate()` which takes the **write** lock — a `ReentrantReadWriteLock` read→write upgrade that self-deadlocks whenever any smoke source is active. `CoupledSystemTest.fullPipelineSteps` hung the JVM forever (verified via `jcmd` thread dump: `WAITING (parking)` at `SmokeSystem.getOrCreate(SmokeSystem.java:57)`). `step()` also mutated sources and grids under only a read lock (data race).
**Fix**: snapshot sources under the read lock → release → mutate via write-locked helpers; snapshot grids under the read lock → step outside; concurrent `step()` calls serialized by a dedicated step mutex.
**Regression test**: `RedTeamTest.smokeStepDoesNotDeadlockWithActiveSources` (sequential + 4 concurrent steppers under 10/15 s watchdogs).
**Result**: full suite now finishes: 102/102 green.

### Fix 5 — Thread-scheduling-dependent render seeds (determinism, spec §29)
`MesrGL/Renderer.cpp::render()` derived per-pixel sample RNG seeds from `rngSeed + threadId·0x9e3779b9 + …`, so the rendered image depended on **which worker thread happened to pick which tile**. The GPU kernel models threadId 0 only.
**Fix**: all threads derive the same per-pixel seed streams (`PCG32(settings.rngSeed)`, no threadId term) — the image is now invariant to tile scheduling; kernel parity preserved.
**Regression test**: two renders with 4 worker threads produce identical pixels and identical ray-counter totals (`test_ray_stats.cpp`).

### Fix 6 — Stubs and build debt (spec §45)
- `SoftwareRayTracer::getTotalRaysTraced()` returned `0; // Not implemented yet` → real per-frame atomic counters for primary + shadow rays, reset per frame, deterministic totals (spec §26 "rays per frame").
- `BVH::validate()/maxDepth()/getStats()/computeNodeDepth()` were **declared but never defined** (link error on any caller) → implemented in `Core.cpp` with structural validation (bounds containment, child index sanity/cyclicity, primitive partition coverage, reachability, depth limit — throws `std::runtime_error` with a diagnosis) and SAH cost estimates.
- `mesrgl-integration/native/mesrgl_bridge.cpp:810`: duplicated `} // extern "C"` closed brace → bridge did not compile; removed.
- Bridge was configured against a 2-day-old static lib (`build_verify`); reconfigured fresh against today's `build/libMesrGL.a`.

---

## 3. TRACEABILITY MATRIX (spec sections 00–45)

Statuses: **PASS** (implemented + verified here) · **PARTIAL** (works, gaps listed) · **ABSENT** (not implemented — see §4).

| § | Area | Status | Evidence / remaining gap |
|---|------|--------|--------------------------|
| 00 | Master directive (implement/test/profile/review/fix) | PARTIAL | This session: 3 critical correctness bugs fixed with regression tests; profiling via benchmark; §4 lists unfinished scope |
| 01 | Absolute graphics independence | **PASS** | `test_graphics_api_isolation` (26 checks) passes; core links no graphics API; bridge `dlopen`s libvulkan at runtime only (no link-time dep); GPU path is software RT in compute shaders, not hardware RT |
| 02 | Software ray tracing | PARTIAL | BVH + Möller–Trumbore + slab tests + primary/shadow/reflection/refraction + iterative path state (GPU) + depth limits + RR (now unbiased) + adaptive sampling. Missing: TLAS/BLAS, instancing/transforms, MIS/NEE |
| 03 | GPU software RT | PARTIAL | SPIR-V kernels (`pathtrace.comp`/`present.comp`) executed on Vulkan compute via the bridge (verified on RADV NAVI14); async dispatch + budgets in executor. Kernels not integrated into MesrGL's own `Backend` (core `selectBackend` always resolves CPU) |
| 04 | BVH optimization | PARTIAL | Median/SAH/binned-SAH builders verified against brute force over 1000 rays ×3 strategies; validate/stats now real. Missing: Morton/LBVH, wide BVH, refit-vs-rebuild (update = rebuild) |
| 05 | Materials | PARTIAL | Diffuse/metallic mirror/dielectric IOR transmission + TIR/emissive; Fresnel-Schlick + dielectric; energy conservation of the indirect estimator now proven (furnace test). Missing: GGX+Smith wired into shading (math exists, unused — Blinn-Phong exp32 in use), alpha cutout, normal maps |
| 06 | Lighting | PARTIAL | Directional/point/spot + ambient, quadratic attenuation, spot cones. Missing: area lights, soft shadows, light alias table, env-map lighting, direct/indirect stat split |
| 07 | Sky & atmosphere | PARTIAL | Procedural starfield (CPU+kernel parity). Missing: Rayleigh/Mie, sun disc, moon, twilight, sky model |
| 08 | Volumetric clouds | ABSENT | — |
| 09 | Fog | ABSENT | — |
| 10 | Weather physics | PARTIAL | Universe module: `WeatherSystem`, `Storm` lifecycle, `WeatherType` registry, deterministic seeds, smooth transitions (WeatherTest). Not wired to rendering |
| 11 | Rain physics | PARTIAL | `PrecipitationSystem`: pooled droplets (no per-drop allocation), gravity+wind, collision, splash (WeatherTest). Rendering interface absent |
| 12 | Snow physics | PARTIAL | `SnowSystem`: accumulation, melt >273.15 K, persistence. Rendering absent |
| 13 | Smoke physics | **PASS** (sim) | Volumetric 8³ grids, buoyancy, semi-Lagrangian advection, trilinear finite-safe interpolation (SmokeFireTest; deadlock fixed this session) |
| 14 | Fire | PARTIAL | `FireSystem`: heat+smoke+fuel+extinguishing, coupled to smoke grid. Rendering (blackbody/mars particles) absent |
| 15 | Fluid physics | **PASS** (sim) | MAC staggered solver, gravity/viscosity/projection, mass conservation ratio 1.0000 (PerformanceTest) |
| 16 | Water | PARTIAL | `OceanSystem`/`WaveSystem` wind-driven params + LOD. Screen-space reflection/refraction absent |
| 17 | Particles | PARTIAL | Pooled SoA-ish droplet arrays. Generic pooled particle framework (fire sparks/debris) absent |
| 18 | World representation | ABSENT (renderer) | Universe module has chunks/streaming/LOD, but no renderer-side chunk→mesh abstraction, no per-chunk BVH updates |
| 19 | MC 1.12.2 integration | PARTIAL | Real client runs (latest.log Sept 4, world join OK, heavy tick lag). **No render-loop adapter exists: zero imports of `io.github.fv2j3dteam.mesrgl` outside the module — the renderer API is currently dead code** |
| 20 | Loader integration | **PASS** | 310 non-empty stress mods (real javac output, 0-byte-class guard), real JAR/ClassLoader paths, `stressTestClient/Server` tasks; renderer-init failure fallback in `MesrGLRendererManager` (API level). Gap: `ModSide` never consumed (no per-mod side filtering) |
| 21 | Resource system | PARTIAL | Mesh/texture/material upload APIs. Missing: caches, versioning, hot reload, async decode |
| 22 | Post processing | PARTIAL | Exposure + 6 tone mappers + gamma/sRGB (CPU+kernel parity). Missing: bloom, vignette, color grading, DoF/motion blur |
| 23 | Anti-aliasing | ABSENT | (jittered/stratified sampling exists; TAA/MSAA-like absent) |
| 24 | Temporal system | PARTIAL | GPU progressive accumulation (frameIndex). Missing: motion vectors, history clamping, reset conditions |
| 25 | Denoising | PARTIAL | Bilateral spatial filter. Missing: temporal, variance-guided, normal/albedo guides |
| 26 | Performance | PARTIAL | Benchmark suite (resolution/SPP/threads/primitives) + this session's real ray counters. Missing: full profiler with per-subsystem timers, export |
| 27 | Memory | PARTIAL | Universe: budgeted LRU cache + high-water counters. Renderer: no arenas/pools/leak detection |
| 28 | Multithreading | PARTIAL | Tile-parallel renderer with fixed seeds; universe deadlocks/races fixed this session; race stress tests exist (RedTeam) |
| 29 | Determinism | **PASS** | Seed-derived streams, deterministicMode, threaded pixel-identity test, run-to-run equality (test_stress), universe no-global-RNG test |
| 30 | LOD & culling | PARTIAL | Universe LOD tiers + LODManager. Renderer: no frustum/occlusion culling |
| 31 | Streaming | PARTIAL | Universe StreamingManager (state machine, coalescing, cancel, memory budget). Renderer-side streaming absent |
| 32 | Visual quality | PARTIAL | Tone-mapped RT imagery; unified sky/cloud/fog/weather visual system absent (§7–9, 22) |
| 33 | Debug UI | PARTIAL | `MesrGLRendererManager.debugLines()` (frame ms, BVH nodes, device) + loading screen stats; no in-game overlay |
| 34 | Loading screen | **PASS** | `MinecraftModMenuTransformer.renderLoading`: progress, stage, mod counts, CPU/GPU/RAM with N/A fallbacks |
| 35 | Configuration | PARTIAL | `RenderSettings` fields + generic `Fv2j3Config`. Missing: quality presets (low→extreme), user-facing config surface |
| 36 | Quality auto-scaling | ABSENT | — |
| 37 | Error handling | PARTIAL | `Result<T>`, renderer state/error strings, fallback policy. Fault-injection tests absent |
| 38 | Testing | PARTIAL | 18 C++ suites + 102 Java tests + bridge GPU tests + 310-mod stress. Missing: soak, startup/shutdown, mod-compat matrix |
| 39 | Stress test | PARTIAL | 310 real mods verified; huge scenes/coords (test_stress, RedTeam). Long-run soak absent |
| 40 | Resolution tests | PARTIAL | Benchmark rows 720p→4K-class; resize API exists. Dedicated resolution/aspect/DPI tests absent |
| 41 | Regression | **PASS** | This session added regression tests for every fix (sub-unit BVH hits, furnace RR, shadow tMax, smoke deadlock, thread determinism) |
| 42 | Security & robustness | PARTIAL | CRC-32 + magic + atomic saves; NaN/Inf sanitisation in solvers. Missing: explicit ray/voxel/particle budgets against malicious content |
| 43 | Build | PARTIAL | gradlew(+.bat), reproducible Java builds; native build needs working cmake (`env -u CMAKE_ROOT` in this sandbox); clean-checkout native build not yet exercised |
| 44 | Documentation | PARTIAL | Javadoc on universe module; READMEs; this audit. Renderer-pipeline/RT-scheduling docs absent |
| 45 | Final audit | — | This document; blocking stubs found by §45 scan (`getTotalRaysTraced`, undefined BVH methods, bridge brace) are fixed as of this session |

---

## 4. HONEST STATUS: WHAT IS NOT DONE

The master spec is a multi-phase program. Beyond the fixes above, these are the load-bearing gaps, in priority order per the spec's own "correctness → independence → determinism → stability → performance → visuals" ordering:

1. **The renderer is not wired into the game (§18–19).** `MesrGLRendererManager` has zero callers. The MC 1.12.2 side needs: a render-loop adapter, chunk→mesh world extraction with dirty-region tracking, upload lifecycle, dimension/resize/pause handling. Without this, all rendering capability is unobservable in-game.
2. **Renderer-side world abstraction (§18)**: chunk streaming, per-chunk BVH refit/rebuild policy, frustum culling.
3. **Missing visual subsystems (§5–9, 22–25)**: GGX/Smith BRDF wiring (math already present), soft shadows + NEE/MIS + light alias table, atmosphere (Rayleigh/Mie/sun/moon), volumetric clouds, fog, TAA/temporal history/denoiser upgrade, bloom/vignette/grading.
4. **GPU executor ownership (§03)**: the Vulkan executor currently lives in the Fv2j3 bridge; MesrGL's own `Backend` still always selects CPU. Decide and document the executor boundary.
5. **BVH evolution (§04)**: Morton/LBVH fast path, wide BVH benchmark, refit-vs-rebuild for dynamic geometry.
6. **Quality/config/auto-scaling/debug overlay (§33, 35, 36)**: presets, dynamic quality, in-game overlay fed from the now-real counters.
7. **Long-run soak, fault injection, resolution matrices (§37–40)**.

---

## 5. VERDICT

- Core software RT correctness: **significantly improved this session** (3 correctness bugs fixed, incl. one that made all sub-unit geometry invisible; cross-validation error −74 %).
- Simulation physics (universe module): **verified green (102 tests)** after a deadlock fix that the previous "COMPLETE" audit missed.
- Loader: fixed and verified against the Aug-30 forensic findings (310 real mods).
- Integration (renderer ↔ Minecraft): **the dominant open gap** — the renderer API is complete and tested at the unit level but has no consumer; the game currently runs on the vanilla pipeline.
- Claimed "COMPLETE" from the prior universe audit is **not reproducible**: its full test run deadlocks without the fix above.


---

# PHASE 46 — REAL MINECRAFT 1.12.2 RENDER PIPELINE INTEGRATION (2026-09-05)

## Objective
Replace the vanilla Minecraft 1.12.2 world renderer with MesrGL inside the real game:
vanilla `EntityRenderer.renderWorld` → Fv2j3 hook → chunk extraction → MesrGL scene/BVH →
software ray tracing (GPU or CPU) → MesrGL framebuffer → Minecraft display, with the
activity overlay confirming MesrGL is active.

## Call chain — traced from the shipped vanilla jar bytecode (not guessed)
`net.minecraft.client.main.Main` → `bib.a()` (Minecraft.runGameLoop) →
`buq.a(FJ)V` (EntityRenderer.updateCameraAndRender) → **`buq.b(FJ)V`
(EntityRenderer.renderWorld)** — the hook target. All notch names used by the
integration were derived from the jar's bytecode (documented in
`MinecraftReflection.java`), with runtime sanity checks.

## Implementation (all in `mesrgl-integration`, package `io.github.fv2j3dteam.mesrgl.integration.minecraft`)
| Component | Role |
|---|---|
| `MesrGLRenderHook` | ASM transformer injecting a head-of-method callback into `buq.b(FJ)V`; returns true → vanilla body skipped, MesrGL produced the frame; false → explicit vanilla fallback (logged) |
| `MinecraftReflection` | All reflective bindings (world, camera entity, chunks, block states, map colors, font renderer, framebuffer, GUIs) + render-thread task scheduling |
| `ChunkSceneExtractor` | Reflection-based chunk→triangle extraction with per-chunk cache, camera-ring priority, per-frame budget, null-retry sweep; faces emitted where neighbor map-color differs; one mesh per quantized color |
| `MesrGLMinecraftRenderer` | initialize/renderWorld/shutdown; scene rebuild throttling; camera from interpolated eye/look; resize handling; per-frame telemetry |
| `MinecraftFramePresenter` | Presents the MesrGL RGBA8 buffer into the vanilla framebuffer via `glDrawPixels` (GL = display surface only, spec 01 boundary kept) + vanilla-FontRenderer activity overlay |
| `WorldAutoJoin` | Test harness: drives Singleplayer → Create New World via scheduled (render-thread) GUI clicks after the loading screen clears |

Wiring: `MinecraftBootstrap` chains the render transformer reflectively (no
compile-time cycle); `bootstrapClasspathFiles` ships mesrgl-integration to the
game JVM; the bridge gained `setMaxSamplesPerPixel`, `setClearColor`,
`reserveSceneCapacity`.

## Verified execution evidence (real game, real GPU)
- Launch: `./gradlew runClient` → Fv2j3 loads mods → vanilla 1.12.2 client boots
  → auto-join creates and enters a world ("Fv2j3 joined the game").
- Hook verified inside the live game (thread dumps show the Client thread in
  `buq.b → MesrGLRenderHook.replaceWorldRender → MesrGLMinecraftRenderer.renderWorld
  → MesrGLJNI.renderFrame`).
- World data received: "tris=412656 chunks=49" (also 656864/81 in an earlier run).
- Vanilla world render replaced: backend lines report
  **backend=GPU_SOFTWARE_RT** continuously (thousands of frames, e.g. frame 9841
  at 20.3 ms; frame 12001 at 7.8 ms) on AMD RADV NAVI14 (Vulkan 1.4), with
  CPU_SOFTWARE_RT fallback also demonstrated (runs before the SPP pinning fix).
- MesrGL framebuffer presented: per-frame BMP dumps of the exact presented buffer
  show ray-traced Minecraft terrain (stepped block geometry, snow field, water)
  with the ACES/exposure pipeline.
- Overlay: the activity line ("MesrGL ACTIVE | backend | device | ms | tris |
  chunks") is drawn over every presented frame through the vanilla FontRenderer;
  the BMP dumps capture the pre-overlay MesrGL buffer by design.

## Additional defects found and fixed during Phase 46
1. **Descriptor-set stale after buffer growth** (`gpu_executor.cpp`): grow-only
   buffers are destroyed+recreated on expansion, but the descriptor set was only
   rebound on output resize — the first dispatch after a scene growth read
   destroyed buffers (device loss, "vkQueueSubmit failed"). Fixed: rebind on any
   recreation (plus null-buffer guard in `bindSceneBuffers`).
2. **vkCreateBuffer SEGV (RADV) on mid-session growth**: fixed by
   `reserveSceneCapacity` — GPU scene buffers, output/accumulator and staging are
   pre-allocated once at initialization (131 MB + 44 MB staging), so the render
   path never grows. A RADV queue wedge when reserving before the first frame was
   fixed by creating output/accumulator and syncing the queue inside the reserve.
3. **Clear color wiped by scene reset**: `resetScene` replaces the MesrGL scene
   (materials/lights/clearColor are scene-scoped) — the sky color is re-applied
   after each reset. The sky is now background radiance (miss color) instead of an
   emissive sky-box: an emissive box occluded every shadow ray from inside itself.
4. **Adaptive sampling made real-time frames impossible**: maxSamplesPerPixel
   defaulted to 64 → up to 64 spp/pixel on noisy terrain (GPU frame >10 s → fence
   timeout; CPU 2.6 s). Pinned to 1 (fixed one-sample interactive mode).
5. **Chunk extraction race**: chunks arriving after the first frame were cached
   as permanently absent; a periodic null-sweep retries them (extraction now
   deterministic across runs).
6. **Auto-join robustness**: clicks are scheduled on the render thread (a direct
   cross-thread call NPE'd in vanilla GUI code), gated on the loading flag, and
   the whole chain retries on failure.
7. **CMake race**: five diag targets generated the same SPIR-V payload
   concurrently (flaky `string SUBSTRING` errors); a single
   `mesrgl_kernel_payloads` custom target now owns generation.

## Honest remaining gaps (Phase 46+)
- Visual tuning: high-contrast lighting (single directional sun, no ambient
  term beyond GI), no textures/animation; map-color palette is blocky.
- The in-game camera at spawn looks straight down, so early dumps are
  sky/terrain-contrast dominated; player movement changes the view normally.
- The on-display overlay was not screenshot-verified on this machine (the
  physical display blanks during headless verification); the overlay is
  verified by code path + absence of fallback logs, and the per-frame BMP dumps
  prove the MesrGL framebuffer content itself.
- Performance beyond SPP=1 at larger radii, texture support, and quality
  auto-scaling remain future work (traceability matrix §4).

## Phase 46 regression status (after all changes)
- MesrGL C++ core: **18/18 suites pass** (incl. graphics-API isolation audit).
- mesrgl-universe: **102 tests, 0 failures**.
- Bridge tests: **PASSED** on RADV NAVI14 (CPU/GPU cross-validation mean 2.19/255).
