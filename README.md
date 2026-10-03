# Polarium

Faster entity rendering for huge crowds in Minecraft (Fabric). The work
Minecraft does for every player and mob on one thread is spread across your
CPU's cores, and crowds of players are drawn on the GPU in a handful of
instanced draws, with every frame drawn the same as the game draws it.

Made by the [Arctic Launcher](https://arcticlauncher.com) team; it works on its
own, in any launcher.

Formerly named Polonium (until 0.26).

## What it does

- **Players drawn on the GPU.** Bodies, armor, capes, held items and name tags
  of other players (and mannequins) are batched: thousands of players take
  around ten draw calls. Each model's shape is uploaded once; per player only
  a small block goes up each frame (where it is and how its head, body, arms
  and legs are turned), and the GPU works out each part's place from it. Skins
  share an atlas so different skins still share a draw.
- **Render states on several threads.** Working out what each entity looks
  like this frame (Minecraft's "extraction") runs on helper threads while the
  render thread carries on with the rest of the frame.
- **Other players' ticks on several threads.** Their client-side tick
  (movement smoothing, animations, head turning) runs in parallel; anything
  that reaches outside the player (particles, sounds, moving between entity
  sections) is held back and done in order on the render thread.
- **Less repeated work.** A player's render state is made in full once a
  tick and only brought up to the frame between ticks; held item models and
  attribute values are kept while they can't have changed.

Things it doesn't draw itself (unusual poses, mods' own entity layers, other
render types) stay on the game's renderer, so they look exactly as without
Polarium. Every frame is drawn the same as the game draws it: no lower detail
far away, no skipped frames for distant players.

## Numbers

Crowd benchmark (client-side players walking about, each with its own skin,
armor, held items and a name tag, all in view), i9-12900KF + RTX 3080 Ti,
Minecraft 26.2 with Sodium, Lithium, EntityCulling, ImmediatelyFast and
FerriteCore:

| Players in view | Without Polarium | With Polarium |
| --- | --- | --- |
| 5,000 | 12 FPS | ~92 FPS |
| 1,000 | — | ~350 FPS |

With a popular mod set on top (Fabric API, Entity Texture Features,
Emotecraft, Not Enough Animations, 3D Skin Layers, Wavey Capes, Simple Voice
Chat, Xaero's Minimap, Jade, AppleSkin, Mod Menu):

| Players in view | Without Polarium | With Polarium |
| --- | --- | --- |
| 1,000 | 33 FPS | ~130 FPS |
| 300 | 55 FPS | ~260 FPS |

## Versions

| Minecraft | Fabric Loader | What's in |
| --- | --- | --- |
| 26.2 | 0.16.9+ | everything |
| 26.1.2 | 0.16.9+ | the CPU side (parallel ticks and render states, kept states); the GPU path is being ported |

No Fabric API needed.

## Compatibility

Tested alongside Sodium, Lithium, EntityCulling, ImmediatelyFast, FerriteCore,
Fabric API, Entity Texture Features, Emotecraft (Player Animation Library),
Not Enough Animations, 3D Skin Layers, Wavey Capes, Simple Voice Chat,
Xaero's Minimap, Jade, AppleSkin and Mod Menu: drawn the same as without
Polarium.

Polarium looks at what other mods change before taking anything over:

- With a shaders mod (Iris, Oculus) or a mod that changes entity models
  (Entity Model Features, Figura) installed, entity models stay on the game's
  renderer; the rest still applies.
- Another mod hooking how player render states are made each frame turns
  kept states off (logged), unless Polarium knows the hook. Mods can do their
  per-frame work themselves through a `polarium:light_state` entrypoint (a
  `BiConsumer<Entity, EntityRenderState>`, called after each bringing up).
- Another mod changing how player models are posed keeps the per-part
  matrices on the CPU (logged), unless its hooks only move the head, body,
  arms and legs.
- Mod layers on players (capes, 3D skin layers) are drawn by the game, the
  game's way; the rest of the player still goes the GPU path.

Turn things off with JVM arguments if something looks wrong:
`-Dpolarium.off=true` (everything), `-Dpolarium.parallelTicks=false`,
`-Dpolarium.parallelExtract=false`, `-Dpolarium.lightStates=false`,
`-Dpolarium.crowd=false`, `-Dpolarium.skeleton=false`,
`-Dpolarium.helpers=N` (helper threads; default two thirds of your CPU's
threads, up to 16). `-Dpolarium.debugTimeline=true` logs where each frame's
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

GPL-3.0-or-later. Polarium's instanced shaders are made from the game's own
at load time; no Minecraft code is included.
