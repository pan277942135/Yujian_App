# Work handoff index — Recognition Master Board Frozen import (2026-10-16)

**Work only / design-only. No Android work.**

## Authoritative entry points

- Control plane: https://github.com/pan277942135/Yujian_App/blob/main/CONTROL_PLANE_REGISTRY.yaml
- AGENTS: https://github.com/pan277942135/Yujian_App/blob/main/AGENTS.md
- PR #145 (Draft, unmerged): https://github.com/pan277942135/Yujian_App/pull/145
- Figma: https://www.figma.com/design/VjN7iFviVN9aKeLv8kJivM/Recognition-Main-Journey-Master-Board-V1?node-id=6-2
- Board node `6:2`, page `0:1`, canvas `5120×2880`
- Baseline PR SHA at handoff: `7158d2168b8cc48ae13eb3e616b4383e60771bd9` — re-read remote HEAD before editing.

## Design conflict that MUST be resolved before a final import

The user's explicit freeze calls for a **two-page** first-use camera guide:
1. P01: portrait phone + horizontal fish; original SHA `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50`
2. P02: landscape phone + horizontal fish; approved original SHA `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` (former P03 image bytes)

The repository PR currently has an inconsistent three-page guide (including unwanted portrait + vertical fish). **Do not import deprecated P02** (`fb092235...`) as an active page. Do not keep P03 as an active page ID. Unless there is a later explicit user-approved reversal, update the design registry to 2 pages and the board to MB-01..MB-15 + MB-R01..MB-R08. Retain old bytes for provenance.

## Source transfer

The chat attachment `Yujian_Figma_Master_Board_Work_Handoff_20261016.zip` contains:
- `README_WORK_HANDOFF.md` — complete step-by-step task with actual links, acceptance gate, and reconciliation instructions.
- `SOURCE_IMPORT_MANIFEST_TWO_PAGE.json` — all 15 primary source paths/SHA/slot IDs/statuses.
- `SOURCE_LINKS.md` — index of verified GitHub control and contract URLs.
- `SHA256SUMS.txt` — hash validation for the 3 included source PNGs.
- `assets/P01_First_Use_Camera_Guide_Frozen.png` → Figma `6:21`
- `assets/P02_Landscape_Horizontal_Fish_Frozen.png` → Figma `6:28`
- `assets/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png` → Figma `6:42`

All 3 included PNGs have been SHA-256 verified locally but **not uploaded into Figma yet**.

## Final outputs

Figma: editable 5120×2880 board with original Frozen image fills; PAGE/STATE/Trigger/Input/Action/Next/Status; accurate REVIEW/MISSING. Only when real source imports have passed, export full-res PNG + PDF. Commit design registry/source audit/report links back to this Draft PR without merging. No AI image generation, screenshots as source substitutes, Android development, CI or APK.

If Work cannot access the chat ZIP, fetch the P01 and historical-P03 *original raw blobs* through the authorized GitHub connector at the linked PR SHA, and look up the landscape Camera original `湖畔拍照识鱼界面(3).png` in the user's Library; match its complete SHA `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18f` before import.