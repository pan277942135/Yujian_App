# YuJian Empty Home V1

## State contract

`HomeScreen` remains the shared `/home` surface. It renders the Empty Home
only when the authenticated archive has loaded with `totalCatches == 0`, or
when the user is a guest. A non-empty archive keeps the existing P01 home
layout and recent-catch carousel.

## Reused interaction contracts

- The existing home camera composition (`camera_outer_ring` + `camera_icon`)
  is shared by both Empty and populated Home states.
- Tapping the camera enters the existing `IdentifyScreen` and system camera
  flow.
- Tapping `从相册选择` enters the same `IdentifyScreen` with its existing
  gallery launcher opened automatically; no second picker implementation is
  introduced.
- Tapping `登录 >` uses the existing Login/Register navigation.
- No audio is started automatically. `lake_morning.mp3` is packaged for a
  later settings-controlled sound feature.

## Asset mapping

| Package asset | Android destination | Use |
|---|---|---|
| `background/empty_home_bg.webp` | `res/drawable-nodpi/empty_home_bg.webp` | 9:16 Empty Home background |
| `title/empty_home_title.png` | `res/drawable-nodpi/empty_home_title.png` | supplied first-catch title |
| `animation/float_motion.json` | `assets/home_empty/animation/float_motion.json` | motion handoff/spec |
| `animation/camera_breath.json` | `assets/home_empty/animation/camera_breath.json` | motion handoff/spec |
| `audio/lake_morning.mp3` | `res/raw/lake_morning.mp3` | disabled by default |

The package's camera artwork is retained as source material, but the runtime
continues to use the established home camera button so Empty Home does not
introduce a second visual or interaction contract.

## Motion

- The supplied lake scene receives a clipped 0–6dp vertical drift over 3200ms
  to keep the existing float visible without redrawing it.
- The existing camera button uses a 1.0–1.045 scale breath over 2600ms in
  Empty Home, matching the supplied `camera_breath.json` timing.
