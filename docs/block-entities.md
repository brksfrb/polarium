# Block entities

Ported from Arctic Client's `perf/chest-hopper`, commits `85dbb1b6` and
`0c82ab41`. Arctic's source repository was only read. Polarium owns this code;
Arctic Client is not a dependency.

| Minecraft target | Block entity culling | Chest GPU instancing |
| --- | --- | --- |
| 1.21.11 | Yes | Yes, 256-instance uniform-buffer batches |
| 26.1.2 | Yes | Yes, 256-instance uniform-buffer batches |
| 26.2 | Yes | Yes, OpenGL render pass |
| 26.3 | Yes | Yes, inside the game's render pass, SPIR-V shaders |
| legacy 1.8.9 | Unchanged | Unchanged |

The older targets use their own RenderPipeline/uniform API through Minecraft's
render passes, with 256 instances per draw (8.5 KiB uniform blocks, below the
portable 16 KiB limit). Single/left/right geometry and shaders are shared in
behavior with the newer adapter. A still frame reuses its instance buffer;
changed frames repack batches. Vanilla render states are retained until the
GPU draw has completed successfully, then their model submissions are skipped.
This retains extraction/light/lid-query overhead on 1.21.11 and 26.1.2, while
removing per-chest model traversal and CPU vertex construction. Other entity
GPU features remain version-dependent.

Culling uses the frame's vanilla frustum and a block box expanded by one block
on every side. Exact vanilla block entity classes only: chests, trapped/ender/
copper chests, shulkers, beds, signs/hanging signs, banners, bells, decorated
pots, skulls, lecterns and campfires. Beds are ordinary block models on 26.2+
and already benefit from chunk rendering. Unknown classes, global rendering,
GUI previews and frames without a known frustum use vanilla. No ray-based
occlusion or reduced draw distance is introduced.

Other block entities retain vanilla geometry, animations, text and lighting.
Modern SignText already caches its laid-out messages; Arctic's sign layout
cache was for legacy renderers and is not installed on these modern versions.
Animated shulkers, patterned banners, pot decorations and sign glyphs remain
on vanilla submission rather than sharing chest geometry.

Chests use the game's baked single/left/right models and chest atlas sprites,
including trapped, ender, copper oxidation/wax variants, Christmas and resource
packs. The vanilla entity fragment shader, fog, lightmap and pipeline state
are preserved. `InstancedPipelines`, `ShaderSources`, `ModelMesh.FORMAT` and
`FrameFences` are shared with entity instancing. Closed instances are cached
per section; lid events wake both halves, packed light is refreshed after light
updates, removed/replaced blocks invalidate entries, and resource reloads
rebuild meshes and texture coordinates. Breaking chests use vanilla geometry
and cracks. The camera-relative integer anchor preserves position precision.
Pipeline errors disable the GPU path and log the error. On the older targets,
the retained vanilla states are submitted in the same frame when any batch
fails. On 26.2/26.3, captured entities are retained until the normal feature
frame closes; a failed layout/upload/draw triggers vanilla extraction and a
recovery pass before the world renderer returns. Later frames use vanilla.
Recovery on the newer targets runs after the normal transparent passes; the
exceptional frame can therefore have different blending where transparent
surfaces overlap recovered chests, but the chests are drawn in that frame.

On 26.2/26.3 the instance and per-draw buffers are written while mapped
(persistently where the driver can), each slot of a three-buffer ring fenced;
a slot the GPU is still using gets a new buffer instead of a wait. There is no
`glBufferSubData` on this path: a megabyte SubData into the instance buffer
crashed Mesa's no-op driver, and an old AMD driver hung in SubData. A driver
without persistent mapping (no GL 4.4 buffer storage) keeps chests on the
game's renderer, with one log line; `-Dpolarium.gpuChests.anyDriver=true`
overrides that. 1.21.11/26.1.2 make new immutable buffers with their data.

Both paths step aside, with a log, for `betterblockentities`, Enhanced Block
Entities, FastChest, Iris (even without an active shader pack), Oculus,
OptiFabric, Immersive Portals, EMF/ETF, Canvas and VulkanMod. With one of these
loaded, the block entity mixins are not applied at all (`PolariumMixins`), so
Polarium adds nothing to that mod's block entity path. Other mods' mixins on the
dispatcher or supported renderers also disable the path conservatively; Sodium,
Fabric's resource-reload registration, EntityCulling (it only leaves block
entities out) and BadOptimizations (it caches the renderer lookup) are allowed.
With EntityCulling loaded, Polarium's own frustum culling and its frustum hook
are left out, since EntityCulling already tests each block entity. A replacement
renderer discovered on a supported block entity also disables the path.
This is deliberately conservative: unknown renderer changes can expand bounds
or change geometry. Minecraft 26.3's built-in Vulkan backend is supported;
VulkanMod is a separate compatibility exclusion.

## Options

Like Polarium's other performance switches, these are JVM properties, read at
startup. Both default to `true`:

- `-Dpolarium.blockEntityCulling=false`: disable block entity frustum culling.
- `-Dpolarium.gpuChests=false`: disable chest instancing independently.
- `-Dpolarium.gpuChests.report=true`: log instance/draw/upload counts every ten seconds.
- `-Dpolarium.off=true`: existing master switch, disables all performance mixins.

Polarium previously had no locale catalogs or options screen. The two new
option labels are in its new `en_us` catalog; there are no other existing
Polarium locales to update. No Arctic locale files or UI are dependencies.

## Reproduce validation

Build each modern target with `./gradlew build -Pminecraft_version=<version>
--max-workers=4`. These builds execute the culling and instance-cache regressions
and `ChestSceneBench` (70,000 chests, 35,000 hoppers, 48 deterministic views).
Reports go to `build/reports/chest-scene-bench-<version>.txt`.

The workload compares vanilla section visibility with individual conservative
culling, and exercises the shared instancing core. It measures CPU bookkeeping
and a vertex-work stand-in, **not in-game FPS or GPU time**. The GPU column is
shared CPU bookkeeping measurement, and excludes vanilla state extraction,
render-pass setup and GPU execution. Older-target reports also measure uniform
batch packing and count the retained vanilla states.
See [block-entity-benchmarks.md](block-entity-benchmarks.md) for measured results.

For paired in-game screenshots, run `./gradlew runClient
-Pminecraft_version=<version> -PblockEntityScene --max-workers=4`. This explicitly
opted-in scene creates a new offline flat world, places all chest materials and
facings plus the other supported block entities, and saves vanilla/GPU pairs
for shut lids, open lids and breaking cracks, then quits. Run output is under
`run/block-entities-<version>`. Install Sodium in that run's `mods` directory to
exercise its dispatcher path (for a development launch, use
`-PblockEntitySodiumDir=/path/to/jars` with Sodium and unpacked Fabric API modules).
`-PchestFailureTest` injects a draw failure; the logs report same-frame vanilla
recovery. `-PchestChristmasTest` makes both vanilla and GPU use the Christmas
selection in this opt-in scene without changing the host clock.
Do not build a different target while a development client is running: Gradle
replaces the shared class output directory.
