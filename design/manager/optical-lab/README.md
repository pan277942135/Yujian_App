# Recognition Optical Lab · Web Review V1

An isolated browser preview of review candidates based on the existing P1–P5 contract. P1–P5 and PR #165 Frozen PNGs remain unchanged. Static and dynamic A/B/C settings are `REVIEW_CANDIDATE` values; they are not Frozen product values and do not represent Android implementation.

## Open the cloud preview

The primary HTTPS preview is composed into the existing NH01 Acceptance Center:

`https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/optical-review/`

The existing static EdgeGlow page links directly to this review route. Static screenshots, dynamic WebM recordings, and provenance evidence are available in the public evidence gallery:

`https://recognition-optical-lab-a1-20261019.pan277942135.chatgpt.site/design/manager/optical-lab/review_v1/evidence/`

The current package exposes static A/B/C candidates, dynamic A/B/C candidates, the three product states, layer decomposition, playback speed, layer toggles, and Fish Focus provenance. The static review gate passed for opening this candidate review; the user has not selected or approved a final direction. Browser playback and WebM output are Web Review evidence only.

## Static optical candidates

| Candidate | Receiving field | Primary paths | Secondary filaments | Intent |
| --- | ---: | ---: | ---: | --- |
| `REVIEW_CANDIDATE_A` · Soft Field | 0.86× | 0.85× | 0.20× | Softer field; lower corners are nearly fog only |
| `REVIEW_CANDIDATE_B` · Balanced Field | 1.30× | 1.08× | 0.34× | More visible blue/gold field; strong upper-right, softer upper-left, short lower traces; recommended for first review |
| `REVIEW_CANDIDATE_C` · Strong Atmosphere | 1.75× | 1.35× | 0.48× | Visibility boundary comparison |

Each keeps P1 path geometry intact and adds an asymmetric, more saturated review-only Receiving Light field: strongest blue at upper right, softer gold at upper left, a restrained gold residue at lower left, and a short weak blue trace at lower right. Web-only per-corner path length/alpha multipliers make the upper-right arc longest, upper-left second, and both lower corners partial/faded. P1–P5 token values and PR #165 Frozen PNGs are unchanged. The candidate composition can be decomposed into receiving field, primary paths, secondary filaments and final composite.

## Dynamic candidates

| Candidate | Field breathing | Path flow | Filaments | Intent |
| --- | --- | --- | --- | --- |
| `DYNAMIC_CANDIDATE_A` · Calm | 3.5% amplitude, independent periods | 0.24 flow scale, short moving segments | 0.22 scale, rare | Most restrained |
| `DYNAMIC_CANDIDATE_B` · Balanced | 6.5% amplitude, independent corner phases | 0.39 flow scale, slow local energy travel | 0.34 scale, intermittent | Recommended first motion review |
| `DYNAMIC_CANDIDATE_C` · Atmospheric | 9.5% amplitude, independent corner phases | 0.52 flow scale | 0.48 scale, more frequent | Upper-bound comparison |

Dynamic A/B/C all build on a receiving-field and path baseline of their stated field/path scales (A 1.00×/0.88×, B 1.30×/1.08×, C 1.65×/1.30×). Each corner has its own breathing period and phase; state changes lower the field as Fish Focus becomes more prominent. The main paths keep a slow, local traveling energy segment instead of a full perimeter chase. Playback and 12-second three-state WebM recordings are available at 0.5×/1×/1.5×. These controls and recordings show browser Canvas output, not Android or a native renderer.

## Fish Focus provenance

- `Production`: **BLOCKED**. This build has no current-head production `FishSubjectResult` / production Mask evidence to bind. No production contour is drawn.
- `Review-only`: `REVIEW_PROXY_NOT_PRODUCTION`, using the Case B photo, a historical detector BBox, and a human-traced alpha fixture. The alpha is not a production Mask. Case B is described by `app/src/androidTest/java/com/yujian/ai/DetectorOrientationRetryRuntimeTest.kt`; source photo SHA-256 is `a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b`.
- The Review-only browser rendering uses a `0.72×` presentation multiplier to soften the hand-traced contour. This affects only the Web review overlay; P2 contract values are unchanged.
- `Blocked`: the default page state. With no selected review proxy or current production result, the page draws no Fish Focus contour.

The review alpha and its BBox/source record are in `review_v1/`. The BBox values come from the previously reported Case B runtime result. The latest PR-head CI compiled instrumentation tests but skipped runtime execution, so those values were not re-run against this commit.

## Run locally

Use the `Recognition-Optical-Lab-Review-V1` artifact attached to the latest PR #166 workflow run. Extract `Recognition-Optical-Lab-Browser-Package.zip`, then from its root run:

```sh
python3 -m http.server 8765
```

Open `http://localhost:8765/design/manager/optical-lab/`. The package preserves repository paths and includes the verified P5 photo, the Case B detector fixture, the review alpha and the exact P1–P5/Frozen inputs. Serve over localhost or HTTPS; `file://` prevents data fetch and SHA verification.

## Evidence and limits

The workflow produces desktop/narrow static captures and four layer decomposition captures for each static candidate. Its result field distinguishes automation checks from visual review. An automated browser check does not establish optical approval.

The default photo remains the P5 test fixture, SHA-verified against its registry binding. It is not source-bound to the Frozen screenshots, so Frozen/Web pixel comparisons remain exploratory. Canvas gradients, compositing, text rasterization and blur are browser approximations. Android Runtime: **not completed**. Native implementation: **not completed**.
