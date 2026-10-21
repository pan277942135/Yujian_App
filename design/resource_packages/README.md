# YuJian NH01 + P01–P03 extracted resource package

This directory preserves the delivered extraction package and its embedded manifest/checksums for review. The original ZIP is stored as numbered binary parts. Rebuild it with:

python3 scripts/materialize_yujian_resource_package.py --output /tmp/Yujian_NH01_P01-P03_Extracted_Resources_V1.zip

The reconstructed ZIP SHA-256 is 45b7d796e9eb8e9162eae111212afbd02d7e8395b9507965b49bcb3a04387689. MANIFEST.json and SHA256SUMS.txt are byte-identical sidecars of the files inside the ZIP. CI checks the ZIP digest, every listed asset digest, and the four frozen source PNGs against their repository authorities.

## Android use boundary

The extraction is a design/review handoff, not a set of production Android drawables. The NH01 avatar/card/peek crops and P01–P03 error-photo crops are marked REFERENCE_ONLY; the main card has baked-in copy and the other crops come from flattened screenshots. The Gemini SVG structures are not frozen photo or layout authorities. None of these crops should be added to app/src/main/res or substituted for live FishRecord media.

Android keeps the responsive NH01 cards data-backed and the P01–P03 guide over the live camera preview. Independent guide hero/error photos remain unavailable, so the guide runtime continues to report ASSET_SOURCE_BLOCKED until production-ready sources are approved.
