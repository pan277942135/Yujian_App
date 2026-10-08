# V1.1 real-photo test set

The executable AndroidTest assets and machine-readable source details are in
`app/src/androidTest/assets/catch_hero_adaptive_v1_1/fixture_manifest.json`.
That build-generated manifest records source and fixture pixel dimensions,
byte sizes, SHA-256, source URL, attribution, license, exact fixture
transformation, and test use. `scripts/prepare_catch_hero_adaptive_fixtures.py`
fetches the three public photographs, verifies their expected byte count and
SHA-256, copies the repository sample, then deterministically regenerates the
explicitly labeled derivatives. CI runs this before building AndroidTest and
again in the emulator job. Downloaded originals, fixture outputs, and their
manifest are retained in the uploaded runtime evidence artifact; third-party
photos are not committed into the application source tree.

## Sources and attribution

| Source | Attribution | License | Source page |
| --- | --- | --- | --- |
| Repository sample `sample_recent_catch.jpg` | Bundled under the repository's authorized test-sample provision; no independent author/license metadata is recorded in the repository | Repository-authorized test use; do not redistribute independently of this project | [Yujian_App asset](https://github.com/pan277942135/Yujian_App/blob/main/app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg) |
| *Fish caught with a rod in Gambia.jpg* | Peter van der Sluijs | CC BY-SA 3.0 (or GFDL as offered by the source page) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Fish_caught_with_a_rod_in_Gambia.jpg) |
| *Portrait of man on boat holding a caught fish (AM 81857-1).jpg* | Photograph by Collins; Auckland War Memorial Museum collection | CC BY 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Portrait_of_man_on_boat_holding_a_caught_fish_(AM_81857-1).jpg) |
| *Flounder in the hand of an angler caught in the Netherlands.jpg* | Peter van der Sluijs | CC BY-SA 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Flounder_in_the_hand_of_an_angler_caught_in_the_Netherlands.jpg) |

Derived fixtures are labeled as such in both the asset filename and JSON
manifest. The 16:9, 9:16, and 1:3 fixtures contain the complete source photo
over a same-source softened backdrop. The edge sample is an explicit crop of
the Flounder source; it retains the full fish, with the nose about 5% from the
right edge. The black-bar fixture adds pure-black top and bottom bands around
the complete repository photo. No derivative is described as an original.

The test set contains no AI-generated fish imagery. No fixture claims a trusted
detector box. Current saved/remote catch records do not provide a persisted
trusted fish box, so the Android screenshot matrix is expected to show
`EVIDENCE_FIT` on both HOME and DETAIL for every sample. The planner's crop and
edge-protection branches are exercised separately by deterministic JVM tests.
