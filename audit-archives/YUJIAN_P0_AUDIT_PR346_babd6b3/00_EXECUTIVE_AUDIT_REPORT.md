# YuJian AI V2 六大核心合同 × PR #346 源码符合性审计

**Task:** `YUJIAN_P0_SIX_CONTRACTS_IMPLEMENTATION_CONFORMANCE_AUDIT_V1`  
**Audit status:** `P0_CONFORMANCE_AUDIT_COMPLETE` — 审计工作完成，源码存在未符合项；此状态不表示实现通过。  
**Source baseline:** `babd6b3ad31ee02f70d74738a27d7f14cc6b5e10`  
**Repository:** `pan277942135/Yujian_App` · **PR:** `#346` · **PR status:** Open / Draft  
**PR head branch:** `fix/26kf-path-boundary-and-fish-gate-v1` · **Base SHA:** `6ec43662ef76c6ffee8530148098649496ab7fb1`  
**Audit time:** 2026-11-24 (UTC; detailed timestamps in `05_AUDIT_MANIFEST.json`)

## Baseline and integrity

The remote branch ref and `refs/pull/346/head` both resolve to the requested exact SHA. The PR page reports `DRAFT`, and its head SHA matches. The audit used a detached, independent clone checked out at that SHA. The pre-existing `/workspace/Yujian_App` workspace was left untouched; it is on local branch `work` at `03db3692669847084d7e48fc3128f85cbf832af9`, so it was not used as the audit source. The PR base SHA was recorded from PR metadata. No source branch, PR metadata, tests, contracts, or product files were changed.

All six V1.0 contracts and the supporting cross-review, acceptance matrix, registry, inventory, manifest, checksum list, and README were read. The six contract SHA256 values match `SHA256SUMS`, Registry, and Manifest. The frozen package itself labels implementation conformance `NOT_ASSESSED` and device acceptance `NOT_RUN`; this audit establishes the first source baseline at the requested commit.

## Executive result

The implementation has substantial production structure: Application-level SlimSAM lifecycle, metadata-based session validation, a shared 60-second attempt deadline, 13 semantic scene states, event-gated transitions, a real mask-bound path ledger, Result handoff preflight plus dual-consumer acceptance, PixelCopy capture, and a hashed ZIP exporter. Several local tests cover those pieces. Those facts do not close the main cross-layer contract gaps below.

| Priority | Findings | Main impact |
|---|---:|---|
| P0 | 4 | Photo identity, committed result identity, pre-navigation Glyph preparation, false frame-completion receipt, native cancellation/termination |
| P1 | 13 | Cache key, per-run model trace, FishPath acceptance order, KF25, geometry/host envelope, evidence gating, profiling, archive validation, video, first-failure, unbounded gates, runtime proof |
| P2 | 0 | No lower-priority issue was separated from the contract-critical gaps in this pass |

There are **17 source conformance findings** (4 P0, 13 P1). Their first affected code paths are identified in `03_ROOT_CAUSE_REGISTER.md`. The repository includes `recognition-ui/src/test/resources/recognition/stage10_failure_5344335d6a51.json`, a deterministic fixture derived from device JSONL at older source commit `3eb481c1f2ca4053fc58f87729f923fb70ef7f50`. It records 9 stages completed, Stage10 entered at 22,444ms and blocked at 23,027ms with progress 1.0; the `ORB_RETURN` gate lacked return render receipt/completion, Glyph readiness, and Result geometry. The recorded return event had zero path segments and was rejected as `EVENT_STAGE_MISMATCH`; geometry was first `LAYOUT_ROOT_PENDING`, then `RESULT_HERO_PENDING`; Result first-presented was 60,006ms. The fixture explicitly says full model replay, video, SessionStore binding, and navigation evidence were unavailable/not observable. This narrows the observed blockage to event/geometry/Glyph gates, but does not identify the first upstream producer failure or prove the cause on the audited SHA. The exact-HEAD root cause for the historical run remains `ROOT_CAUSE_UNCONFIRMED`.

## Highest impact findings

1. `SelectedImage.photoSha256` is the hash of the normalized JPEG bytes. The separate `sourceBytesSha256` and `canonicalPixelSha256` required by the contracts are not present. Model, mask, geometry, path, and handoff code reuse that one field.
2. `RecognitionResultSnapshot` exists as an immutable data class, but the store constructs it on demand from mutable session state. It has no committed `snapshotId` or `contentHash`, so it is not the frozen, addressable V2 result fact.
3. Glyph geometry is extracted from `TextLayoutResult` inside Result UI `onTextLayout`. Navigation readiness does not require a prepared Glyph snapshot. The production path therefore does not have the required shared, pre-navigation `GlyphPathProvider`.
4. `RecognitionGlyphDrawReceipt.drawPassSequence` is produced in Compose `drawWithContent` and is treated as rendered readiness. That confirms a draw pass, not `FRAME_PRESENTED` or a matching PixelCopy. `NAME_STABLE` can be accepted without proof that the intended pixels reached the display.
5. The shared deadline and coroutine timeouts do not terminate synchronous ONNX `session.run` calls. Production does not use ORT `RunOptions` termination or record native-return/termination states. The code comment explicitly documents cooperative cancellation; a blocked native call can outlive the attempt timeout and hold serialized runtime work.

## C01–C24 and A01–A19

All 24 cross-contract conflict decisions and all 19 acceptance rows were inspected individually. The complete clause-level matrix is `01_REQUIREMENT_CONFORMANCE_MATRIX.csv`. Static pass areas include the KF00–KF25 ID set, the 13-stage ordering and 6000ms sum, dynamic SlimSAM batch metadata acceptance, stage 13 substage ordering, trusted mask/path gates, and dual Controller/SessionStore handoff acceptance. The matrix marks those `PASS_STATIC_ONLY` where a current device run or contract-complete identity is absent.

A notable direct conflict is C21: the recorder does initialize 26 checkpoint rows, but KF25 remains `NOT_REACHED` when no save tap occurred; its export policy intentionally leaves that value unchanged instead of writing `NOT_APPLICABLE`.

## Main production path

`YujianApplication` creates and preloads the application-owned SlimSAM manager. `YujianApp` starts the recognition pipeline from the recognition screen, the pipeline runs detector then classifier, and `RecognizingScreen` joins detector/classifier/subject-model state, validates mask/geometry, registers the fish-contact path, and drives the scene controller. The path ledger exports a content-hashed FishPath snapshot; the Controller accepts it and the App callback updates the SessionStore. Result milestones are emitted from Result UI, then synchronously checked by Controller and SessionStore. Evidence subscribes to stage/frame/result data and packages files through its ZIP verifier.

The implemented handoff consumer is a positive baseline: it preflights the same event against both owners, enforces sequence/time/stage/path/Glyph checks, mutates both synchronously on Main, and returns a receipt. Its event still lacks V2 `snapshotId`, `snapshotHash`, and `handoffId`, and its glyph render premise is weaker than frame-presented proof.

## Completion and limits

- Source baseline and contract hashes are verified.
- C01–C24, A01–A19, all 26 KF mappings, eight Gradle modules, and primary producer/consumer paths are recorded.
- Findings are separated into source-confirmed contract mismatches and runtime claims that remain unconfirmed.
- Existing tests were inspected but not run. No APK, emulator, or device flow was run for this audit.
- The next fix work should start with P0-01, P0-02, P0-03 and P0-04, then address P1 items in dependency order from photo/result identity through evidence acceptance.

See `02_CROSS_LAYER_CALL_GRAPH.md`, `03_ROOT_CAUSE_REGISTER.md`, `04_TEST_AND_EVIDENCE_INDEX.md`, and `SOURCE_MODULE_MAP.md` for traceable detail.
