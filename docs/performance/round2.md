# Performance round 2 — 2026-11-15

Branch `perf/round2` starts at `origin/main` (`22b049e`). Two prerequisite
commits from `perf/block-entities` supply the chest instancer, its same-frame
recovery and the 1.21.11 compatibility fixes. Unmodified main failed the
1.21.11 build before these prerequisites. The software submission fix from
`polarium-189-cpu-perf` is also included, with fresh tests and measurements.

The host has no `/dev/dri` device. All rendered numbers below are Mesa CPU
rendering under Xvfb, with `LP_NUM_THREADS=4`, RD8 and the built-in fixed-seed
benches. They do not establish RTX/hardware gains or the user's previous
vanilla/Polarium multipliers. Rendered runs in each paired series were serial.
This shared host has other workloads; small changes are inconclusive.

## 1.8.9: why standing still differs

Standing still eliminates the terrain walk. A fresh exploratory baseline
reported terrain setup 0.00 ms and terrain draw 11.52 ms. Optimizing the walk
again cannot fix this workload. Software rendering serializes geometry work
and rasterization unless submissions are flushed partway through the frame.
The imported vertex-budget submission fix cuts region batches at approximately
20,000 vertices and flushes only software GL drivers. Hardware GL is untouched.
It preserves the geometry, draw order, fog, blend state and framebuffer path.

Longer validation used 900 warm-up frames, then 600 measured frames per run,
with a frozen yaw of 123 degrees and pitch 25. All runs drew 201 sections.
The world retained its normal changing entity population (83–103 loaded).

| Run order | Pipeline | Mean frame ms | p99 ms | Mean FPS |
| --- | --- | ---: | ---: | ---: |
| 1 | Off | 19.91 | 26.71 | 50.2 |
| 2 | On | 13.99 | 18.69 | 71.5 |
| 3 | On | 13.35 | 19.06 | 74.9 |
| 4 | Off | 21.98 | 42.17 | 45.5 |
| Mean of two runs | Off → on | **20.95 → 13.67** | — | **47.7 → 73.2** |

That is 35% less frame time, approximately 1.53x the baseline throughput on
this software renderer. It is not a 1.53x-over-vanilla claim. Timed GL phases
can stay flat while whole-frame time falls because the flush allows the
client and software raster workers to overlap. Switch off with
`-Dpolarium.pipeline=false`; the vertex budget is
`-Dpolarium.pipeline.vertices=20000`.

## Block entities beyond chests

26.2/26.3 shulker models and banner supports can use the existing cube-model
instancer, with the game's own baked geometry, animation, atlas, tint, light,
overlay and shader. UVs use the same sprite offset/scale; the sprite is never
copied into the player skin atlas. Banner flag/pattern layers, blending,
crumbling overlays, foreign sprite namespaces, replacement models and unknown
UV mappings stay vanilla. The scan also retains a model entirely on vanilla
when it has an unsupported overlapping pass.

| Target | Signs | Banners | Beds | Shulkers | Hoppers |
| --- | --- | --- | --- | --- | --- |
| 1.8.9 | Vanilla | Vanilla | Terrain | Not in game | No dedicated renderer |
| 1.21.11 | Vanilla | Vanilla | Vanilla | Vanilla | No dedicated renderer |
| 26.2 | Body is terrain; text vanilla | Support instanced; cloth/pattern vanilla | Terrain | Instanced | No dedicated renderer |
| 26.3 | Body is terrain; text vanilla | Support instanced; cloth/pattern vanilla | Terrain | Instanced | No dedicated renderer |

The newer game's standing-sign renderer only submits text; its sign body and
bed body are already baked terrain models. They do not need another block
entity instancer. The 1.21.11 uniform-buffer renderer remains chest-specific;
this round does not port arbitrary articulated models to it.

The new path defaults to **hardware devices only**. Software-device scene
results were mixed, so llvmpipe/softpipe/lavapipe and devices identified as CPU
retain vanilla. `-Dpolarium.gpuSpriteModels=false` disables it;
`-Dpolarium.gpuSpriteModels=true` explicitly forces it for diagnostics.
`-Dpolarium.gpuSpriteModels.report=true` logs eligibility decisions once per
model/sprite. Existing compatibility exclusions still apply.

The larger scene places 117 shulkers (four colors, six directions), 11 signs
and 11 banners. Pairs below turn only the sprite-model path off/on; chest
settings stay unchanged. These are median whole-frame CPU costs, including
software rendering/pacing, not GPU or renderer-only timings. The first pair
also includes warm-up effects. 26.3 falls back to Minecraft's built-in Vulkan
backend when Xvfb cannot create its requested GL context.

| Target | Closed before → after ms | Opening before → after ms | Cracks before → after ms |
| --- | ---: | ---: | ---: |
| 26.2, forced software | 13.09 → 16.63 | 14.38 → 15.64 | 14.50 → 14.04 |
| 26.3, forced software Vulkan | 22.58 → 18.73 | 16.45 → 17.23 | 28.35 → 16.27 |

There is no consistent software win here, hence the automatic device guard.
No hardware performance claim is made. The original chest-only scene was
also measured before the extension; its shut/open/cracks GPU medians on 26.2
were 15.56 / 16.20 / 14.51 ms. It is a different scene and must not be compared
as an extension speedup.

The injected draw failure recovered **128 original model submissions in the
same frame** on both 26.2 and 26.3. Captured submits survive upload/prepare/draw
failure and retain their original pose, sprite and animation input. Future
frames use vanilla after the GPU path disables itself. Like the prerequisite
chest recovery, recovery occurs after the normal feature frame closes;
transparent overlap ordering in that exceptional frame is not proven equal.
Do not interpret the test as proof of every transparent failure case.

Paired screenshots were inspected. A closed-model region in the 26.2 pair
had RGB mean absolute difference 0.276/255, maximum channel difference 6;
there was no geometry omission. These are not pixel-equality assertions;
world rendering/raster rounding and time still vary between screenshots.
No distance, detail, visibility or texture-quality setting is reduced.

## Terrain profiles on the modern targets

The existing terrain bench ran first, then again after the changes. It uses
20 seconds warm-up and 10 one-second FPS samples, rotating above the hills.
The first exploratory runs overlapped other local work; use this table as
context, not as a controlled speedup comparison. The final runs included a
JFR profile. The sprite extension normally stays off on this CPU device.

| Target | Initial FPS | Final FPS | Interpretation |
| --- | ---: | ---: | --- |
| 1.8.9 | See static ABBA table | See static ABBA table | Controlled switch comparison above |
| 1.21.11 | 13.2 | 19.0 | No modern terrain optimization claimed |
| 26.2 | 19.9 | 19.6 | Within host/run variability |
| 26.3 | 13.1 | 19.2 | Software Vulkan; no terrain optimization claimed |

JFR execution/native samples from the final 15 seconds: 690/751 (91.9%) of
1.21.11 client samples and 667/739 (90.3%) of 26.2 client samples were inside
OpenGL draws. On 26.3, 681/764 (89.1%) were truncated FFM native downcalls;
this cannot attribute the time precisely within Vulkan. Worker samples also
show biome lookup and chunk compilation, but they are not the dominant
client-frame cost. No entity, particle, or chunk rebuild rewrite was shipped
without a demonstrated frame-time benefit in these profiles.

## Reproduction and checks

Build each requested modern target with `GRADLE_OPTS=-Xmx3g ./gradlew build
-Pminecraft_version=<version> --max-workers=4`. Legacy is a separate project:
`./gradlew -p legacy build -Pminecraft_version=1.8.9 --max-workers=4`.

For new model pairs: `./gradlew runClient -Pminecraft_version=26.2
-PblockEntityScene -PspriteScene --max-workers=4`; substitute 26.3 for Vulkan.
Add `-PspriteFailureTest` to inject a draw failure. These explicitly opted-in
scenes create offline test worlds and screenshots; they do not sign in.
The scene flag forces the path on software devices so it can be validated.

For legacy, set `polarium.bench=terrain`, `polarium.bench.freeze=true`,
`polarium.bench.distance=8`, `polarium.bench.warmup=900`,
`polarium.bench.frames=600` and the pipeline switch as VM arguments in the
Loom client run. The legacy runtime used JDK 21 (the installed JDK 25 lacks
libjawt), while builds used JDK 25. Set LD_LIBRARY_PATH to that runtime's
`lib` and `lib/server` when launching LWJGL 2 under Xvfb.

New headless tests cover driver gating, vertex budgets, eligible models and
sprites, blending/decal exclusions and atlas endpoints/interior mapping.
The prerequisite chest/culling tests and scene workloads also run in builds.

Final validation: all five supported Polarium targets built (including
26.1.2); each modern build passed 19 headless tests, and legacy passed two.
`python -m unittest discover -s tests` passed two checker regressions.
`python mixcheck.py 1.21.11 26.1.2 26.2 26.3` passed all four. Its hierarchy
parser was corrected to traverse default-method interfaces and erase generic
bounds before locating superclasses; the previous false positives for
`Level.getEntityCollisions` and `Minecraft.runAllTasks` did not require game
hook changes. New sprite capture is scoped to the world's render frame;
special item previews in menus retain vanilla rendering and cannot leave
recovery submissions for a later world frame.
