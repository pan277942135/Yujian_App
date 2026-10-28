# YuJian Empty Home V1

## State contract

`HomeScreen` remains the shared `/home` surface. It renders Empty Home only
when the active fish-record list is empty. Login state never selects the Home
state. A non-empty archive renders the Normal Home layout.

## Reused interaction contracts

- The supplied camera artwork (`camera_outer.png` + `camera_inner.png` +
  `camera_icon.svg`) is shared by both Empty and populated Home states.
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
| `home_motion/float/*` | `assets/home_motion/float/` | bobber and water ripple motion |
| `home_motion/camera/camera_breath.json` | `assets/home_motion/camera/camera_breath.json` | shared camera breath |
| `audio/lake_morning.mp3` | `res/raw/lake_morning.mp3` | disabled by default |

The package's camera artwork is retained as source material, but the runtime
continues to use the established home camera button so Empty Home does not
introduce a second visual or interaction contract.

## Motion

- The supplied bobber uses 0→4px→0 and -1°→1° rotation over 4500ms.
- The supplied outer/inner ripples use 0.95→1.08→0.95 scale and
  0.35→0.08→0.35 opacity over 5000ms.
- The shared camera button uses the supplied 1.0→1.04→1.0 scale and
  1.0→0.92→1.0 alpha breath over 3000ms.
