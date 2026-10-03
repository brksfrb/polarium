# Polarium

**Huge crowds of players, smooth.** Polarium makes Minecraft draw thousands of
players at high frame rates, with every frame drawn exactly as the game draws
it: no lower detail far away, no skipped animation frames, no lost features.

Made for big servers, events, arenas, lobbies and bot tests: anywhere
hundreds or thousands of players are on screen at once.

## How fast?

Crowd benchmark: players walking about, each with its own skin, armor, held
items and a name tag, all in view (i9-12900KF + RTX 3080 Ti, Minecraft 26.2,
with Sodium, Lithium, EntityCulling, ImmediatelyFast, FerriteCore):

| Players in view | Without Polarium | With Polarium |
| --- | --- | --- |
| 5,000 | 12 FPS | ~92 FPS |
| 1,000 | — | ~350 FPS |

With a popular mod set on top (Entity Texture Features, Emotecraft, Not Enough
Animations, 3D Skin Layers, Wavey Capes, Simple Voice Chat, Xaero's Minimap,
Jade, AppleSkin, Mod Menu): 1,000 players 33 → ~130 FPS.

## How?

- **Players drawn on the GPU.** Bodies, armor, held items and name tags of
  thousands of players take around ten draw calls. Each model's shape is
  uploaded once; per player only a small block goes up each frame, and the
  GPU works out where each body part goes.
- **All your CPU cores.** Working out what each player looks like, and other
  players' client-side ticks, run on helper threads instead of only the
  render thread.
- **No repeated work.** A player's render state is made in full once a tick
  and only brought up to the frame between ticks.

## Compatibility

Works alongside Sodium, Lithium, EntityCulling, ImmediatelyFast, FerriteCore,
Entity Texture Features, Emotecraft, Not Enough Animations, 3D Skin Layers,
Wavey Capes, Simple Voice Chat and more. Polarium checks what other mods
change and steps aside where it can't be sure: with a shaders mod (Iris) or an
entity model mod (Entity Model Features, Figura), entity models stay on the
game's renderer and the rest still applies.

No Fabric API needed. Client-side only: works on any server.

## Versions

Minecraft 26.2 (everything) and 26.1.2 (multi-threaded ticks and render
states; the GPU path is being ported). More versions are coming.

## Something looks wrong?

Turn parts off with JVM arguments: `-Dpolarium.off=true` (everything),
`-Dpolarium.crowd=false` (the GPU path), `-Dpolarium.skeleton=false`,
`-Dpolarium.lightStates=false`, `-Dpolarium.parallelTicks=false`,
`-Dpolarium.parallelExtract=false`. Please report it on
[GitHub](https://github.com/brksfrb/polarium/issues) with your log.

Open source (GPL-3.0). Made by the [Arctic Launcher](https://arcticlauncher.com) team.
