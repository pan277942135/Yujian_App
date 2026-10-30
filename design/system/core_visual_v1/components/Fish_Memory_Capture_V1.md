# Fish Memory Capture V1

Status: **FROZEN VISUAL AUTHORITY**  
Owner: **Shared Fish Memory Capture**  
Raster: `design/system/core_visual_v1/components/assets/Fish_Memory_Capture_V1_Frozen.png`

One shared capture surface attaches PHOTO or VIDEO to the currently open FishRecord, using the existing camera preview, safe-area, shutter, and record-control language. See `Camera_Button.md`.

- Captured media bytes remain unchanged and attach to the existing FishRecord.
- PHOTO uses a still-photo shutter. VIDEO uses a record control and mode selection.
- Close returns to the same FishRecordDetail; this surface does not create a new record or start Recognition.
- Exclude detector UI, AI contour, Recognition Processing stages, species confidence, model identity, and debug presentation.
