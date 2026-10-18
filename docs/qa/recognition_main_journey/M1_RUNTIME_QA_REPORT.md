# M1 Runtime QA Report

Parent: YUJIAN_M1_PARALLEL_DELIVERY_V1 / PR #163
Audited remote source SHA: 81aefb1c0eae08df5ea6f451fc7c65be89a9c191
Integrated candidate SHA: not yet published
APK SHA-256: NOT_RUN
GitHub Actions Run ID for the candidate: NOT_RUN
Device: hosted Ubuntu 24.04 / API28 x86_64 Pixel 2 / 1080×1920 is configured but not yet executed.

## Individual N001–N010 classifications

| Gate | Result | Current evidence |
|---|---|---|
| N001 · API28 Runtime Matrix | BLOCKED_INFRA | Prior PR run 39086281726 built but skipped Runtime; hosted Preflight and matrix have no Run ID. |
| N002 · Recognition Frozen | NOT_RUN | V3.1 and guide assertions are in Track A/C candidate worktree; no exact-SHA API28 result. |
| N003 · J01–J20 journeys | NOT_RUN | Full journey execution record is absent; no candidate Runtime execution. |
| N004 · Normal Home A4.1 | NOT_RUN | P1 gate is not executed on candidate SHA; source fixture/provenance and device evidence remain outstanding. |
| N005 · Evidence package | NOT_RUN | No candidate screenshots, boundsInWindow, ROI Pixel Diff, Motion Trace, MP4, device profile artifact or runtime logs. |
| N006 · APK provenance | NOT_RUN | No candidate APK pair or exact APK SHA-256 exists. |
| N007 · Phase 3-B provenance | REVIEW_REQUIRED | Matrix records provenance/source gaps; no exact SHA mapping or independently accepted authority evidence. |
| N008 · Focused report | NOT_RUN | Reports describe static/audit evidence; no exact Runtime gate summary or APK provenance. |
| N009 · Human release | NOT_RUN | Release package is incomplete; no manual approval requested or granted. |
| N010 · First-use Guide asset authority | REVIEW_REQUIRED | Approved M1 decision is two pages, but static N010 still requires three PNGs; PR #162 is Draft and not integrated, so authority sync must be resolved. |

## Journey and catalog status

The catalog validator reports 66 base cases and 10 E2E scenarios with valid structure. Every execution result remains NOT_RUN. This is contract validation only.

| Journey family | Case status |
|---|---|
| J01–J02 · Empty Home/entry | NOT_RUN |
| J03–J10 · Permission, guide, capture, processing, recognition exceptions | NOT_RUN |
| J11–J16 · Result, edit, save and error recovery | NOT_RUN |
| J17–J18 · Home return, persistence and recreation | NOT_RUN |
| J19–J20 · FishRecordDetail A/B | NOT_RUN |
| M1-SUPP-SAVE-DETAIL-01 · Save → Home → same record A/B → Back | NOT_RUN; AndroidTest not yet compiled/run |

## Hosted runner and formal gate status

| Result | Classification |
|---|---|
| Build Once APK pair | NOT_RUN on candidate SHA |
| API28 hosted Preflight | NOT_RUN |
| Recognition Frozen | NOT_RUN |
| Camera Runtime | NOT_RUN |
| Empty Home | NOT_RUN |
| Normal Home | NOT_RUN |
| Login | NOT_RUN |
| Data Sanitization | NOT_RUN |
| Fish Guide | NOT_RUN |
| Visual Geometry / Glyph / ROI Pixel Diff | NOT_RUN |
| Motion timing / trace / MP4 | NOT_RUN |
| Accessibility | NOT_RUN |
| Independent review | NOT_RUN |

Track A and Track C unit/static checks on their own branch SHAs are recorded in their reports; they do not close these exact-SHA Runtime classifications. Run 39086281726 is historical Build PASS with skipped Runtime jobs and is not reused. C01, C02, the 658/669 Page Contract validator failure and C03 hosted environment remain open.
