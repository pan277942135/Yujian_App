# Recognition Processing Sound Spec V1

Status: **FROZEN**

Policy: **NONE**

Scope: **Recognition Processing only**.

## Frozen rule

Recognition Processing is silent.

Do not play:

- ambient AI sound;
- scanner / sonar / sweep sound;
- per-state transition sound;
- fish-located sound;
- looping Processing audio;
- RESOLVE completion sound.

No audio asset is required for Recognition Processing V1.2.

## Boundaries

- Recognition Result / Issue surfaces own any downstream sound decision separately.
- Camera capture audio, OS audio, media playback, or other platform-owned sounds are outside this Processing contract.
- Runtime must not add decorative sound to make Processing feel more active.

## Change control

Adding any Processing sound requires a new explicit design decision and version. Runtime implementation must not invent one.
