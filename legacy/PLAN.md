# Polarium for 1.8.9: optimisation plan

Status: items 1 to 6 are written and build in CI (switches: terrain, regions, clouds, entities, openfaces); only item 1 has run in a game. Items 2 to 6 are untested.

Rule for every item: the picture stays pixel-identical (or differs by float rounding far below one
colour step), and each one has an off switch (`-Dpolarium.<name>=false`) so one jar gives before/after.

## Measured so far (fixed bench scene, about 550 visible sections, render distance 11)

Frame costs in ms (two runs, vanilla): terrain setup 1.2 to 2.1, terrain draw 1.3 to 2.0, entities 0.4 to 0.8,
clouds 0.34 to 0.49, display 0.2 to 0.4, rest of renderWorld small. Total 3.4 to 6.2 ms depending on machine load.
Terrain walk (item 1): setup 1.2..2.1 down to 0.8..1.0 ms, about 10 to 15 % of the frame.

## Items, biggest first

### 1. Terrain walk without garbage (built, measured)
Done. One open point: the self-check reported 3 mismatches in 26 000 walks (698/691, 727/725, 721/720 sections).
Cause found on paper: the check reads each section's visibility data a second time, and the chunk builder thread
swaps that data in when a rebuild finishes, so the two reads can differ. The walk now remembers what it read and
the check uses that. To confirm: one verify run after the next build; any mismatch left would be a real bug.

### 2. Region display lists: one call per region instead of four per section (largest item, about 1.0 ms of 1.3 to 2.0)
Today per section and layer: pushMatrix, translate, glCallList, popMatrix (4 native calls, about 550 sections times
2 to 4 layers). Plan: compile each section's vertices relative to a region corner (a 128 by 128 block square, full
height) instead of the section corner, and draw each region's sections for a layer with one `glCallLists`
(list ids in an `IntBuffer`) under one translate. Roughly 550 x 4 x layers calls become about 60 x layers.
- Patch points: `BuiltChunk.method_10158` (the `offset(-pos)` of the vertex buffer), the 1.000001 section scale
  matrix (`method_10174`: its centre moves to the section centre in region coordinates), `AbstractChunkRenderManager`
  translate (`method_9770`) and `ListedChunkRenderManager.render`, plus `WorldRenderer.renderLayer(RenderLayer)`
  to bucket sections by region.
- Order: opaque, cutout and cutout-mipped layers are bucketed by region (order inside a layer cannot change the
  picture: no two sections share a surface). Translucent keeps the game's back-to-front order exactly, drawn as
  runs of consecutive sections of one region.
- Precision: vertex coordinates are multiples of 1/16 or finer and stay exact in float at region size; adjacent
  sections in one region now share exact coordinates (slightly fewer rounding cracks than vanilla). Region-to-region
  joins round like vanilla section-to-section joins do today.
- Off in VBO mode (`GLX.supportsVbo()` true): the flag is decided in `WorldRenderer.reload`, which already rebuilds
  every section when the option changes.
- Risks: translucent resort uses the buffer's own offset (sort stays consistent because the offset is the same
  one used to build); mods that read section lists directly (none on 1.8.9 Fabric known).
- Limits: only helps display-list mode (the default); with VBOs on, nothing changes (see item 9).

### 3. Fancy clouds from a cached list (about 0.3 to 0.4 ms, fancy clouds only)
The game rebuilds about 3 000 vertices every frame, twice (a depth pass and a colour pass). The geometry only
depends on the integer cell of the cloud position, the cloud colour, and whether the camera is above or below
the clouds; the fractional shift and the height become a translate. Cache the list, rebuild when the cell, the
colour or the above/below state changes, draw it twice. Float difference: `(x - frac)` in the game versus
`x + (-frac)` on the GPU: far below a pixel. Fast clouds (cloud mode 1) are a single quad already, untouched.

### 4. Entities by entity, not by section (about 0.3 ms)
The game visits every visible section, looks the chunk up in a hash map and checks its entity list, then checks each
entity. With 100 to 200 loaded entities it is cheaper to walk the entity list, find each one's section, test whether
that section is in this frame's visible list, and render in the game's order (visible-list index, then position in
the slice list: sort the few hits). Entities not in a slice list (`addedToChunk` false) are skipped as before.
Block entities: same idea only if the visible-section scan stays above 0.1 ms in the profile.

### 5. Open-faces cache (about 0.12 to 0.14 ms)
`getOpenChunkFaces` floods up to 4 096 blocks of the camera's section on every walk. Result depends only on the
camera block position and the blocks of that section, so cache it keyed on (block position, the loaded world
chunk object, a counter bumped on every client block change). Any change or chunk replacement recomputes. The cost
returns while blocks change every frame, as in vanilla.

### 6. Faster walk body (maybe 0.3 ms of the remaining 0.65 to 0.85; built as a stamp array: a neighbour already reached is now rejected without touching its section object)
Per visited section: positions, boxes and visibility data are read through several objects. Keep them in flat
arrays indexed by section index (positions and boxes refreshed when the storage re-centres, which already
resets every section), walk on the arrays. Uncertain gain: memory misses are the cost, which only a profile shows.

### 7. Skip work the frame did not need
- `renderLayer` filter loop and `helpers.add` per layer: replaced by item 2's bucketing, built once per frame from the
  visible list instead of once per layer.
- Translucent resort check and `chunkBuilder` bookkeeping: unchanged (cheap).

Built (part of region switch): the per-layer filter pass and the render manager's second read of each section are one
pass over the walk's visible array (`RegionBatches.renderVisible`); the manager's helper list is no longer filled.

### 8. GL state
`GlStateManager` already skips repeated state; the remaining repeated calls are the matrix pushes removed by item 2.
Nothing separate to do.

### 9. VBO mode (not planned)
With VBOs on, the same region idea would be a region VBO plus `glMultiDrawArrays`. Display lists live in video
memory on NVIDIA and AMD already, so moving to VBOs only pays when item 2 is not enough. Not worth the risk before
item 2 is measured.

## What moves to the GPU
Chunk geometry stays resident in video memory (display lists), and item 2 turns its submission into a few large
commands, so the CPU hands the GPU far fewer, larger jobs. Frustum and occlusion tests stay on the CPU: on the
measured scene they cost under 1 ms and the GPU is not the limit (28 % busy). Cloud geometry also becomes resident
(item 3). A GPU occlusion-query pass would add a frame of latency (pop-in) and was left out for that reason.

## Not worth doing, and why
- Entity, particle, weather batching: no measured cost in the scene; check a profile of a real world first.
- Anything that shortens distance or detail: ruled out by the brief.
- The Arctic HUD: separate, in the launcher repo.
- Chunk rebuild speed: smoother, not faster on average; separate task.

## Expected total
Items 2 to 5 together save about 1.8 ms of the 3.4 to 6.2 ms, which is roughly 1.4 to 1.8 times the FPS of the
current jar in this scene, on top of item 1. A 10 times gain is not reachable without dropping quality.

## Order of work
1 (verify only), 2, 3, 4, 5, 6. After each: build in CI, one off/on pair, a verify run where there is one.
