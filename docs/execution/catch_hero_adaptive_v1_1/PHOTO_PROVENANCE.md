# V1.1 real-photo test set

The two public source photographs are versioned under
`app/src/androidTest/assets/catch_hero_adaptive_v1_1/source_photos/`; the
repository sample is copied from the existing main asset. The machine-readable
`fixture_manifest.json` records source and fixture dimensions, bytes, SHA-256,
source URL, attribution, license, exact transformation, and test use.
`scripts/prepare_catch_hero_adaptive_fixtures.py` validates each source against
its SHA-256 and byte count, then deterministically rebuilds the explicitly
labeled fixtures. CI does not fetch images during a build, so Wikimedia rate
limits do not make the runtime test nondeterministic. The generated fixtures
are included in the AndroidTest APK and runtime evidence archive.

## Sources and attribution

| Source | Attribution | License | Source page |
| --- | --- | --- | --- |
| Repository sample `sample_recent_catch.jpg` | Bundled under the repository's authorized test-sample provision; no independent author/license metadata is recorded in the repository | Repository-authorized test use; do not redistribute independently of this project | [Yujian_App asset](https://github.com/pan277942135/Yujian_App/blob/main/app/src/main/assets/home_normal/fish_record/sample_recent_catch.jpg) |
| *Fish caught with a rod in Gambia.jpg* | Peter van der Sluijs | CC BY-SA 3.0 (or GFDL as offered on the source page) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Fish_caught_with_a_rod_in_Gambia.jpg) |
| *Portrait of man on boat holding a caught fish (AM 81857-1).jpg* | Photograph by Collins; Auckland War Memorial Museum collection | CC BY 4.0 | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Portrait_of_man_on_boat_holding_a_caught_fish_(AM_81857-1).jpg) |

Fixture transformations are described in the manifest. The 16:9 and 9:16
fixtures contain the complete named source photo over a softened ambient canvas
made from that same photo. The 1:3 fixture contains the complete Auckland
source photo over its same-source ambient canvas. The edge sample is an
explicit crop from the Auckland photograph; it retains the full fish, with the
tail close to the left edge. The black-bar fixture adds pure-black top and
bottom bands around the complete repository photo. Derived fixtures are
clearly labeled and are not presented as original photographs.

No AI-generated fish imagery is included. No fixture claims a trusted detector
box. The saved/read `RemoteCatch` chain does not provide a persisted trusted
fish box, so the Android runtime screenshot matrix is expected to show
`EVIDENCE_FIT` on both HOME and DETAIL for every sample. The planner's crop and
edge-protection branches are exercised separately by deterministic JVM tests.
