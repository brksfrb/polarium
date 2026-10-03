# Changelog

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
