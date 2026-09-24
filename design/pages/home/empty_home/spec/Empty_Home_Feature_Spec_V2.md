# Empty Home Feature Spec V2

## Scope

Android HomeScreen EMPTY state only. `fishRecords.isEmpty()` selects EMPTY; session state does
not. The NORMAL Home and downstream capture, recognition, fish-detail and fish-memory behavior
are unchanged.

## Frozen content and behavior

- Top left: 渔见 and 拍照收藏每次渔获.
- Top right: guest 登录 >, or the existing authenticated account slot.
- Hero: 现在，轮到你 / 记录第一条鱼。 with its gold brush underline and equivalent accessible text.
- Scene: a clear morning lake, mountains, mist, rod, line, bobber and exactly one bobber ripple.
- CTA: 对准鱼获，拍一张; primary capture button; 从相册选择.
- Capture invokes the existing `onIdentify`; album invokes existing `onAlbumClick`; account invokes
  the existing login/profile callback. No duplicate navigation, CameraX, or picker is permitted.

## Experience guardrails

The empty state feels like a quiet, breathable morning rather than a database placeholder, game
loop, loading state, or dark/high-contrast promotion. There is no automatic audio. Native controls
remain accessible and respect safe insets while the scene remains full bleed.
