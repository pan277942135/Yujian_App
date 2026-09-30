# Empty Home Sound Spec V1

Status: **FROZEN**

## Product decision

Empty Home V2 has **no automatic audio playback**.

No sound is triggered by:
- page entry;
- idle/environment motion;
- bobber or ripple motion;
- camera breathing or gold-rim sweep;
- login/account entry;
- album entry;
- capture-button tap.

The visual morning-lake atmosphere must remain complete and usable with no audio.

## Deferred packaged asset

`app/src/main/res/raw/lake_morning.mp3` is present in the Android repository as a deferred,
disabled-by-default resource from an earlier Home package.

It is **not** part of the current Empty Home V2 sound authority and must not be started by the
current runtime.

The asset may only become active in a future sound-contract version after an explicit product
decision covering user settings, playback policy and acceptance evidence.

## Playback policy

Current V1:

- auto-play: prohibited;
- loop: none;
- gain: N/A;
- fade-in/fade-out: N/A;
- interruption/mixing: N/A;
- silent-mode behavior: N/A because playback is disabled;
- media-volume behavior: N/A because playback is disabled;
- accessibility/settings: no sound setting is required for the current no-playback experience.

## Change gate

Enabling ambient sound requires all of:

1. new Sound Spec version;
2. canonical audio asset registration with SHA-256 and audio metadata;
3. explicit user/settings behavior;
4. Android runtime mapping;
5. playback tests and device evidence;
6. global experience registry update.

Runtime code must not infer permission to play audio merely because an audio file exists in the APK.
