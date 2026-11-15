# Block entities

Ported from Arctic Client's `perf/chest-hopper`, commits `85dbb1b6` and
`0c82ab41`. Arctic's source repository was only read. Polarium owns this code;
Arctic Client is not a dependency.

| Minecraft target | Block entity culling | Chest GPU instancing |
| --- | --- | --- |
| 1.21.11 | Yes | No: predates Polarium's GPU backend |
| 26.1.2 | Yes | No: Polarium's entity GPU backend is also unavailable here |
| 26.2 | Yes | Yes, OpenGL render pass |
| 26.3 | Yes | Yes, inside the game's render pass, SPIR-V shaders |
| legacy 1.8.9 | Unchanged | Unchanged |

The older modern targets have a different RenderPipeline/buffer API, rather than
26.2's bind groups, texel buffers and vertex-format bindings. Their GPU backend
remains unported; they get culling rather than a separate raw-OpenGL renderer.
The 1.21.11 build also adapts existing item/model names, benchmark world creation
and mapping-dependent entity tick lambdas; GPU-only accessors are not installed
where their target members do not exist.

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
Pipeline errors disable the GPU path and log the error; subsequent frames use
vanilla. As with Arctic's adapter, a failure after extraction can lose the
captured chests for that one frame.

Both paths step aside, with a log, for `betterblockentities`, Enhanced Block
Entities, FastChest, Iris (even without an active shader pack), Oculus,
OptiFabric, Immersive Portals, EMF/ETF, Canvas and VulkanMod. Other mods' mixins
on the dispatcher or supported renderers also disable the path conservatively;
Sodium and Fabric's resource-reload registration are allowed. A replacement
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
an experiment on the shared core on older targets, not an enabled adapter.
See [block-entity-benchmarks.md](block-entity-benchmarks.md) for measured results.

For paired in-game screenshots, run `./gradlew runClient
-Pminecraft_version=<version> -PblockEntityScene --max-workers=4`. This explicitly
opted-in scene creates a new offline flat world, places all chest materials and
facings plus the other supported block entities, and saves vanilla/GPU pairs
for shut lids, open lids and breaking cracks, then quits. Run output is under
`run/block-entities-<version>`. Install Sodium in that run's `mods` directory to
exercise its dispatcher path. The scene also runs on culling-only targets.
