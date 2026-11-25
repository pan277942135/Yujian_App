# Low Result V1.1

Status: FROZEN — UPDATED BY FROZEN VISUAL AUTHORITY

Authority: design/pages/recognition/design/07_Result_Low_Frozen.png.

## Initial composition

The canonical Hero target is approximately 322 × 178dp (aspect 1.81). The initial page visibly includes:

- 无法确认是什么鱼;
- 手动选择鱼种;
- 重新拍摄;
- vertical Metadata Card;
- Story Card;
- Dual CTA.

Metadata and Story are editable before species resolution and remain local draft state.

## Pending save behavior

If an unresolved Low user taps either CTA:

1. remember the requested destination;
2. open Species Selector with LOW_MANUAL;
3. commit the selected species;
4. resume the requested destination automatically.

If the user returns unconfirmed, preserve entered Metadata/Story and clear the pending destination. Retake abandons the current attempt. An unresolved Low state never creates an unknown-species FishRecord.

After manual selection, reuse the normal resolved Result composition. Species selection does not rerun Recognition.
