# Fixture: FIXTURE_HUAWEI_EAC685C9_V1

## Sources and integrity

- Device: HUAWEI CMS-AL20, Android 16 / API 36, 1280×2832 at 3.5 density.
- Evidence ZIP: `f90816d652ef6f6907ce753e5fd4a72d7f2459619bb7735548abe8ccbb045275` (16204260 bytes).
- MP4: `fee1858c7e5e47687bf7de2a7bfa15716f6755e6b798c285dc4630f9691b7392` (8633990 bytes).
- Evidence archive entry hashes: 37 verified, 0 failures.
- Source commit in evidence: `67f17f0eb6a80233438cb8b00111a55d6767a6d8`.

## Observed recognition snapshot

- SlimSAM status: `READY`; trusted Mask SHA `4b554bb4a2af48d139c2b7994c342961a42e9a0abe92800e7e96f9f72d403de4`.
- Mask version: `339270510316287117`; geometry version `1437474289190970691`.
- Contour: 789 points, closed; ROI `[716, 1440]`.
- P*: `[0.5604575, 0.9372829]`.
- Processing exported a valid 3-segment return snapshot with producer/content digest `dd95a3fdaf5142c80f99ab8fa711fd03c347c6fdde1e4b59d04b81a1a488f0a9`.

## Binding failure

At event 345, SessionStore rejected the already received path as `REJECTED_MASK/PATH_MASK_MISMATCH` even though identity hashes, Mask SHA prefixes and Mask versions matched. Processing Photo had geometry version `0` and no source P* (``); the path had geometry version `1437474289190970691` and P* `0.5604575,0.9372829`. The path was not pending afterward, so a later Processing Photo update could not bind it.

At the later Stage10 stall, the runtime still reported `resultGeometryState=PENDING` and unmet `[orbReturnRenderReceiptReady, orbReturnComplete, glyphAvailable, resultGeometryReady]`. The logged stale `CONTOUR_C` ABSORB rejection is preserved as a separate diagnostic; it is not used as the primary cause.

## Replay boundary

The fixture preserves the full original path values and event records. Source photo dimensions/full transform are not in the device package. The deterministic unit test uses a labeled synthetic mapping to validate the join predicate only; it does not claim a device geometry replay.

## Deterministic source fix

`RecognitionFishPathSnapshotBinding.evaluate()` now treats empty/default Processing Photo Mask identity, P*, and geometry version `0` as missing join inputs. It retains the same immutable path as `RECEIVED_PENDING`; a real disagreement in a populated Mask identity remains `MASK_MISMATCH`, and a populated but conflicting geometry remains `GEOMETRY_MISMATCH`. The device's original producer digest remains verifiable under snapshot schema `0`; current exports use schema `1` and include the model identity and viewport P*.

The versioned `RecognitionSnapshot` is an alias for the Processing-authored path value that Controller and SessionStore pass unchanged. It carries attempt/photo/model/Mask/geometry identities, source and viewport P*, fish path ID, coordinate space, and schema version. Current production exports pass `SlimSamSegmenter.MODEL_VERSION`; the historical device archive did not record that version and is labeled `NOT_RECORDED_IN_DEVICE_EVIDENCE` in the replay fixture.

The event consumer retains `EVENT_STAGE_MISMATCH`. A stale contour event is rejected without advancing the event sequence or preventing a later valid `ORB_RETURN`; replay of the already accepted return event leaves the state unchanged and is rejected as a duplicate. These are source regression results, not a new device replay.

## Current run scope

The archived Huawei evidence is from source `67f17f0eb6a80233438cb8b00111a55d6767a6d8`, before the fix. It captures 6 of 26 checkpoints; 7 are missing and 13 were not reached. `RESULT_EVIDENCE_NOT_AVAILABLE` remains the device archive's result status. The new code has not yet completed an exact-HEAD API36 production run or a post-fix Huawei run.
