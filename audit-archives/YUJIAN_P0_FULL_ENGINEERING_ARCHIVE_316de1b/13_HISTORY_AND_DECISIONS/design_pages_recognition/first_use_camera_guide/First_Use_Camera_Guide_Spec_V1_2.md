# First-use Camera Guide Specification V1.2

Status: ACTIVE · PRODUCT OWNER CONFIRMED  
Date: 2026-11-03  
Supersedes: V1 three-page active sequence for page count, phone orientation, navigation and runtime bindings.

## 1. Product objective

Teach a first-time user how to frame a fish clearly without asking the user to rotate the phone. The phone remains portrait throughout the guide. The two steps differ only in the orientation of the fish within the frame.

## 2. Active two-step sequence

### P01 · 竖屏拍摄示意

- Phone orientation: portrait.
- Fish orientation: horizontal.
- Instruction: 手机保持竖屏，整条鱼横向完整放入取景范围
- Hero: one complete horizontal-fish example.
- Error examples: 鱼头不在画面 / 鱼体过小 / 遮挡过多, all showing horizontal fish.
- Primary action: 下一张 → P02.

### P02 · 竖拍鱼类拍摄示意

- Phone orientation: portrait.
- Fish orientation: vertical.
- Instruction: 手机保持竖屏，整条鱼竖直完整放入取景范围
- Hero: one complete vertical-fish example.
- Error examples: 鱼头不在画面 / 鱼体过小 / 遮挡过多, all showing vertical fish.
- Primary action: 开始拍摄 → close the guide and return to Capture Ready.

## 3. Explicitly excluded

The former P03 横屏拍摄示意 (landscape phone + horizontal fish) is not an active step. It must not appear in:
- Android Runtime guide pager or page indicator;
- active Design Manager guide navigation or responsive page selector;
- active page/role asset bindings;
- unit / AndroidTest page navigation or runtime evidence requirements;
- camera guide screenshot / overlay / diff evidence gates.

The former P03 Frozen PNG and its SHA remain preserved as historical provenance only. It is not deleted or rewritten.

## 4. Assets

Exactly eight independent candidate images are active:
- P01: horizontal correct Hero + three horizontal error examples.
- P02: vertical correct Hero + three vertical error examples.

The two supplied page backgrounds remain separate Runtime resources. Candidate PNG bytes, source aspect ratios, SHA-256 values and GENERATED_CANDIDATE provenance remain unchanged. The 2026-11-02 product-owner Runtime approval remains valid for these eight unique images and the two backgrounds; candidates are not relabeled Frozen.

## 5. Interaction, entry and completion

- Entry: only after Camera permission is granted and CameraX provider + Preview are Ready.
- Gallery entry bypasses the first-camera guide.
- 跳过 closes the guide and persists completion; no shutter request.
- P01 下一张 advances to P02.
- P02 开始拍摄 closes the guide, persists completion and returns to Capture Ready; it never triggers the shutter.
- Previous is available on P02 only. System Back / Back / dismiss do not persist completion.
- The guide touch shield prevents taps from reaching the shutter or Gallery underneath.

## 6. Responsive and accessibility

- The phone instruction remains portrait on both pages, regardless of outer viewport layout.
- Outer UI respects actual WindowInsets and viewport dimensions; this does not create a landscape-phone teaching step.
- Hero and examples use contain / ContentScale.Fit; never rotate, crop, stretch or re-encode source fish photos.
- Three error examples use three columns when width permits and wrap to two columns at compact widths.
- Short screens scroll through the full guide; all controls remain reachable and meet the registered minimum target.
- Page indicator is exactly two steps (01 / 02, 02 / 02).

## 7. Acceptance boundary

Basic automated acceptance for this integration consists of unit tests, AndroidTest APK compilation, APK asset verification and Android Lint. API 28 device verification is explicitly not part of this task; the product owner will verify the delivered APK on a real device.
