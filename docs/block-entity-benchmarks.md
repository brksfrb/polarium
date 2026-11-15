# Block entity validation — 2026-11-15, POLBE2

All four modern targets built with `./gradlew build
-Pminecraft_version=<target> --max-workers=4`: 1.21.11, 26.1.2, 26.2, 26.3.
All 16 headless tests passed on each. Legacy 1.8.9 also built successfully
with `./gradlew -p legacy build -Pminecraft_version=1.8.9 --max-workers=4`.

## 1.21.11 production and development scenes

The scene places 104 chest halves, of which 102 are in drawing range/view,
including every facing/material, normal/trapped doubles, ender, copper/waxed
variants, a shaded chest, moving lids and a breaking overlay. The normal
frame logs **102 chests in three instanced draws**. Breaking a visible chest
reduces that to 101; vanilla draws its geometry and cracks.

Both development and actual remapped production jars completed the scene.
1.21.11 was checked without Sodium and with Sodium 0.8.14 + Fabric API
0.141.6. The production Sodium run also enabled Christmas selection and a
resource pack replacing the Christmas atlas images and changing the entity
vertex shader. Both vanilla and instanced images used the pack's textures
and shader tint, including on open double chests. Production validation also
caught and fixed the previous compatibility guard's dependence on named
renderer packages; class references now survive Fabric remapping.

Paired screenshots were visually inspected and compared numerically. In the
initial plain 1.21.11 run, mean absolute channel differences (0–255 RGB) were
0.478, 0.080 and 0.033 for shut/open/breaking pairs. Small differences include
clouds, particles, the tutorial popup and raster rounding; this is not an
automated pixel-equality assertion.

Whole-frame CPU timings below include Mesa software rendering and frame
pacing, measured over approximately 250–400 frames per sample. These small
scenes do **not** establish a hardware FPS improvement, and some pairs are
flat or slightly slower. They validate the enabled renderer and provide
reproducible context for Burak's hardware test.

| 1.21.11 scene | Shut vanilla → GPU median ms | Open vanilla → GPU median ms | Breaking vanilla → GPU median ms |
| --- | --- | --- | --- |
| Production, no Sodium | 10.16 → 9.92 | 9.67 → 9.85 | 9.82 → 9.84 |
| Development, Sodium | 8.02 → 7.70 | 7.57 → 7.64 | 7.58 → 7.47 |
| Production, Sodium + Christmas + custom atlas/shader | 9.19 → 8.62 | 9.61 → 8.81 | 10.21 → 8.82 |
| Development, no Sodium + Christmas + custom atlas/shader | 10.17 → 9.91 | 9.83 → 9.95 | 9.88 → 9.91 |

The 26.1.2 scene also completed, logging 102 chests in three draws. The 26.3
scene ran on Mesa Vulkan after OpenGL initialization failed under Xvfb.

## Failure and compatibility checks

`-PchestFailureTest` deliberately throws after extraction, at draw time.
1.21.11 logged 104 retained vanilla chest states for the same frame; its
submission guard stays false unless all batches and pass closure succeed.
Both 26.2 and 26.3 logged recovery of 102 captured chests through vanilla in the failed
frame. Normal world rendering then continued and completed the scene.
The newer adapter also queues recovery after layout/upload/capture failures.
Its recovery pass runs after normal world features: transparent overlap can
blend differently in that exceptional frame, but captured chests are drawn.

Separate metadata-only `betterblockentities` and `iris` Fabric fixtures test
the compatibility exclusion in a production 1.21.11 launch. These are not
visual tests of actual BBE geometry or an actual Iris shader pack; both mod
IDs conservatively disable the block entity path, even without shaders.
Unknown entity classes, renderer replacements and unsupported pipelines use
vanilla. Christmas is forced only inside the explicitly enabled test scene.

## ChestSceneBench: 70,000-chest shared CPU workload

70,000 double-chest halves, 35,000 hoppers, 48 deterministic views, OpenJDK 25.
The vertex stand-in transforms/writes 72 vertices per chest; it omits render
state allocation, model traversal, light/neighbour queries and GPU uploads.
Shared-core timings below exclude the game adapter and GPU. They are **not**
FPS or measured total renderer speedups.

| Target | Vanilla → culled model submissions/frame | CPU vertex stand-in vanilla → culling ms | Shared core still/turning/10 lids ms |
| --- | --- | --- | --- |
| 1.21.11 | 29,067 → 17,460 | 10.1 → 6.3 | 0.10 / 0.11 / 0.11 |
| 26.1.2 | 29,067 → 17,460 | 8.4 → 5.3 | 0.10 / 0.10 / 0.11 |
| 26.2 | 29,067 → 17,460 | 8.3 → 5.2 | 0.09 / 0.11 / 0.11 |
| 26.3 | 29,067 → 17,460 | 8.4 → 5.3 | 0.10 / 0.11 / 0.12 |

With instancing, these chests require zero vanilla model submissions and zero
CPU chest vertices. Older adapters still extract approximately 17,460 vanilla
states as a same-frame fallback. In the still view, 29,808 GPU instances use
118 bounded uniform-buffer draws on 1.21.11/26.1.2, versus up to three texel-
buffer draws on 26.2/26.3. Repacking changed older-target batches took 0.020 ms
(CPU only), using a 1.03 MB padded instance buffer. Stable frames reuse that
buffer; the small camera/draw headers are still uploaded every frame.

Vanilla's vertex stand-in writes 75.3 MB/frame, or 45.3 MB after culling. The
shared cache holds 2.24 MB of instance data for all 70,000 chests; the view's
core instance payload is about 0.93 MB. Hoppers have no block entity renderer
on these targets and contribute no renderer calls.

Reproduce using the commands in [block-entities.md](block-entities.md).
Generated jars, game worlds, screenshots and build directories are cleaned
after the personal launcher has consumed the jars. Sources and this report
remain in the repository.
