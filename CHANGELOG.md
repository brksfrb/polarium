# Changelog

## 0.26.0

- Players' bodies are posed on the GPU: each sends its six moving parts
  (head, body, arms, legs) instead of a matrix per part. 5,000 players:
  81 → 92 FPS.
- Works with popular mod sets: Player Animation Library (Emotecraft) and
  Entity Texture Features no longer turn the GPU path and the parallel
  render states off; with them, 3D Skin Layers, Wavey Capes, Not Enough
  Animations and Voice Chat, 1,000 players went from 33 to ~130 FPS.
- Other mods' hooks are now told apart by what they hook: only hooks into
  what Polonium skips turn a feature off (logged with the mod and method).
- Each helper thread looks up its model copies once per job, not per model.
- Polonium has an icon.

## 0.25.0

- A player's render state is made in full once a tick and only brought up
  to the frame between ticks (kept states).
- Render states and visibility worked out in one pass on the helper threads.
