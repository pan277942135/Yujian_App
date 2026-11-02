# R05 Adapter (documentation only)

Keep existing MIT `WaterCausticsField`; never fork a second algorithm. Existing `WaterCausticsRenderer` owns photo-area Bitmap lifecycle and cannot be wired as the Orb adapter directly. OrbAdapter should project into a real alpha sphere/core water mask, cache by size/profile/tier, share `WaterLightFrame.opticalReferenceElapsedMs`, keep two persistent buffers, cancel stale generations, and avoid recycle/draw race. No runtime change this task.
