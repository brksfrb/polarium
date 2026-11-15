# Block entity validation — 2026-11-15

`./gradlew build -Pminecraft_version=<target> --max-workers=4` passed on
1.21.11, 26.1.2, 26.2 and 26.3. All 15 headless tests passed on each target.
`./gradlew -p legacy build -Pminecraft_version=1.8.9 --max-workers=4` also passed;
legacy rendering is unchanged. CI now includes 1.21.11.

## ChestSceneBench

70,000 chest halves, 35,000 hoppers, 48 deterministic views, OpenJDK 25 on this
build host. CPU timings are illustrative and vary by JIT/load. This is the
shared-core workload, not an FPS claim, in-game renderer profile or GPU timer.
The vertex stand-in transforms and writes 72 vertices per chest; it omits render
state allocation, model traversal, light queries, neighbour queries and upload.

| Target | Renderer calls/frame before → after | CPU vertex stand-in before → culling (ms) | Culling test (ns/chest) | GPU core still/turning/10 moving lids (ms) |
| --- | --- | --- | --- | --- |
| 1.21.11 | 29,067 → 17,460 | 11.2 → 7.1 | 12.8 | Not enabled |
| 26.1.2 | 29,067 → 17,460 | 7.9 → 5.0 | 7.5 | Not enabled |
| 26.2 | 29,067 → 0 | 7.9 → 5.0 | 7.6 | 0.10 / 0.10 / 0.11 |
| 26.3 | 29,067 → 0 | 7.9 → 5.0 | 7.5 | 0.10 / 0.07 / 0.12 |

Culling removes 40% of chest renderer calls in this hall (38% in the smaller
mixed storage-base SceneBench). Vertex data: 75.3 MB/frame before, 45.3 MB with
culling. Instancing: no CPU model vertices, zero instance uploads with a stable
view, 0.93 MB while turning; up to three draws. Cached instance blocks occupy
2.24 MB on the CPU, plus the frame array and GPU buffers. Hoppers have no block
entity renderer on these targets and contribute zero renderer calls.

## In-game scenes

The opt-in scene completed on all four modern targets. On 26.2 it was run both
without Sodium and with Sodium 0.9.2 + Fabric API 0.161.0, using Mesa software
OpenGL. On 26.3 it also ran on Minecraft's built-in Vulkan backend. GPU runs
logged 102 chests in three draws, with zero uploads/bakes in a still frame.
Paired images cover every chest material/facing, single/double/trapped/ender/
copper chests, a shaded area, open lids and breaking cracks. Visual inspection
and image differences found no missing chest geometry or large lighting changes;
particles, clouds, HUD timestamps and small raster rounding differences remain.
These are software rendering checks, not hardware performance measurements.

The scene does not currently assert pixel equality automatically. Christmas
selection and resource-pack atlas lookup follow vanilla but were not separately
exercised with a December clock or custom pack. No real BBE or Iris shader-pack
version was installed for visual comparison. Separate metadata-only Fabric
fixtures for `betterblockentities` and `iris` verified that each loaded mod ID
logs the vanilla fallback and returns `compatible=false`.

`mixcheck.py` found no missing new block entity hooks. It still reports existing
inherited-method false positives (`Level.getEntityCollisions`,
`Minecraft.runAllTasks`); actual client starts exercise those mixins successfully.
The added 1.21.11 adapters also remove GPU-only accessors targeting absent fields
and match the entity tick lambda's unique invocation independently of its mapped
synthetic name.

Generated builds, worlds, screenshots and local Gradle output are removed after
validation; the benchmark and scene sources remain reproducible in the repo.
