# Polonium

Faster entity rendering for huge crowds in Minecraft (Fabric). The work
Minecraft does for every player and mob on one thread is spread across your
CPU's cores, and crowds of players are drawn on the GPU in a handful of
instanced draws, with every frame drawn the same as the game draws it.

Made by the [Arctic Launcher](https://arcticlauncher.com) team; it works on its
own, in any launcher.

## What it does

- **Players drawn on the GPU.** Bodies, armor, capes, held items and name tags
  of other players (and mannequins) are batched: thousands of players take
  around ten draw calls. Each model's shape is uploaded once; per player only
  its pose goes up each frame. Skins share an atlas so different skins still
  share a draw.
- **Render states on several threads.** Working out what each entity looks
  like this frame (Minecraft's "extraction") runs on helper threads while the
  render thread carries on with the rest of the frame.
- **Other players' ticks on several threads.** Their client-side tick
  (movement smoothing, animations, head turning) runs in parallel; anything
  that reaches outside the player (particles, sounds, moving between entity
  sections) is held back and done in order on the render thread.
- **Less repeated work.** Render states, held item models and attribute
  values are kept between frames where they can't have changed.

Things it doesn't draw itself (unusual poses, mods' own entity layers, other
render types) stay on the game's renderer, so they look exactly as without
Polonium.

## Numbers

Crowd benchmark (client-side players walking about, each with its own skin,
armor, held items and a name tag), i9-12900KF + RTX 3080 Ti, Minecraft 26.2
with Sodium, Lithium and EntityCulling:

| Players in view | Without Polonium | With Polonium |
| --- | --- | --- |
| 5,000 | 12 FPS | ~50 FPS |
| 1,000 | — | 140–180 FPS |

Work in progress: the next step moves per-frame animation onto the GPU, so the
cost per frame stops growing with the crowd.

## Versions

| Minecraft | Fabric Loader | What's in |
| --- | --- | --- |
| 26.2 | 0.16.9+ | everything |
| 26.1.2 | 0.16.9+ | the CPU side (parallel ticks and render states, kept states); the GPU path is being ported |

No Fabric API needed.

## Compatibility

- Works alongside Sodium, Lithium, EntityCulling, ImmediatelyFast and FerriteCore.
.
- With a shaders mod (Iris, Oculus) or a mod that changes entity models
  (Entity Model Features, Figura) installed, entity models stay on the game's
  renderer; the rest still applies.

Turn things off with JVM arguments if something looks wrong:
`-Dpolonium.off=true` (everything), `-Dpolonium.parallelTicks=false`,
`-Dpolonium.parallelExtract=false`, `-Dpolonium.crowd=false`,
`-Dpolonium.helpers=N` (helper threads; default two thirds of your CPU's
threads, up to 16). `-Dpolonium.debugTimeline=true` logs where each frame's
time goes.

## Building

```
./gradlew build                                  # Minecraft 26.2
./gradlew build -Pminecraft_version=26.1.2
```

The jar lands in `build/libs/`. Code that differs between Minecraft versions
sits in `//#if MC >= 26.2 … //#else … //#endif` blocks, resolved at build time.
`python mixcheck.py 26.2 26.1.2` checks every mixin target against those
versions without starting the game.

## License

GPL-3.0-or-later. Polonium's instanced shaders are made from the game's own
at load time; no Minecraft code is included.
