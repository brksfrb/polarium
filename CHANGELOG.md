# Changelog

## 0.29.0

- A server can find out Polarium is installed, so it can recommend it
  (or skip a "slow crowds" warning). If the server lists the channel
  `polarium:hello` among its plugin channels, Polarium answers once per
  connection with `{"version":"<its version>"}` (plain UTF-8 JSON, no player
  or computer data). A server that doesn't list the channel never hears
  anything. No Fabric API needed. With Arctic Client installed, Arctic says
  the hello for Polarium (only one mod can read the server's channel list).

## 0.28.1

- Fix: waiting for the GPU before reusing a buffer (new in 0.28.0) could
  wait forever if the GPU never reported a pass done, hanging the game. It
  now waits at most 50 ms, then uses fresh buffers instead.

## 0.28.0

- **Faster everyday frames, not just crowds.** GUI text is cheaper: text
  with no right-to-left characters skips the bidirectional analysis (same
  result, checked against the game's), the scoreboard sidebar's lines are
  kept between frames while they're unchanged, and text drawn again as the
  same text at the same place reuses its laid-out glyphs. In a server lobby:
  about +10% (1,270 to 1,390 FPS on an RTX 3080 Ti, i9-12900KF); limited to
  two slow cores, about +4%.
- A notice in game when a shaders mod (Iris, Oculus) or an entity model mod
  keeps entity models off the GPU, so slow crowds there aren't a mystery.
- Name tags: what decides them (shown, text, score) is worked out once per
  tick; where they hang and the distance, every frame. Checked against the
  game's on every frame.
- Players' capes and flight are only brought up to the frame when shown or
  gliding.
- Mods that pose players only sometimes (emotes) can tell Polarium when
  they do (a `polarium:posing` entrypoint); the rest of the time the GPU
  poses those players. Player Animation Library is recognized.
- GPU buffers are waited on before reuse when a frame draws the crowd more
  than once (the inventory's player preview).

## 0.27.1

- Fix a crash with a spinning compass (no target, another dimension, or a
  recovery compass with no death yet) in the hotbar while other players or
  mobs held one: their states were made on helper threads, and spinning
  compasses share one random source with the HUD's. Item models that change
  by themselves (compass, clock, cooldown, bow, local time, ...) are now
  worked out one at a time on any thread; everything else as before.

## 0.27.0

- **Polonium is now Polarium.** New name, new icon; the mod id is
  `polarium`, its JVM switches are `-Dpolarium.*`, and mods doing their own
  per-frame work register a `polarium:light_state` entrypoint.
- A player's render state carries over ticks where nothing it's made from
  changed (pose, flags, equipment, skin, ...), instead of being made in full
  on every tick: at 5,000 players that was half of the work of making
  states. Checked field by field against states made in full.
- The helper threads' states are waited for only where the game first
  reads them, so the render thread sets up the rest of the frame meanwhile.
- Far-to-near sorts of big crowds (bodies, items, name tags) are about
  three times quicker; the bulk check is made with each render state.
- Fixes: the debug screen's entity count (read 0); a hidden name tag's
  place is cleared as the game does; the 10-second status lines only show
  with `-Dpolarium.debugReports=true`.

## 0.26.0

- Players' bodies are posed on the GPU: each sends its six moving parts
  (head, body, arms, legs) instead of a matrix per part. 5,000 players:
  81 → 92 FPS.
- Works with popular mod sets: Player Animation Library (Emotecraft) and
  Entity Texture Features no longer turn the GPU path and the parallel
  render states off; with them, 3D Skin Layers, Wavey Capes, Not Enough
  Animations and Voice Chat, 1,000 players went from 33 to ~130 FPS.
- Other mods' hooks are now told apart by what they hook: only hooks into
  what Polarium skips turn a feature off (logged with the mod and method).
- Each helper thread looks up its model copies once per job, not per model.
- Polarium has an icon.

## 0.25.0

- A player's render state is made in full once a tick and only brought up
  to the frame between ticks (kept states).
- Render states and visibility worked out in one pass on the helper threads.
