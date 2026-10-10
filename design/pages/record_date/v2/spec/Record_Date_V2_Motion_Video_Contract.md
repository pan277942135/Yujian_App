# 记录日期 V2 · Motion / Video 规范边界

**Status: ACTIVE_CLOSURE / 尚未冻结具体转场时长**

遵循共享 `design/system/core_visual_v1/tokens/motion_tokens.json`、`design/system/motion/README.md` 的 `alive, not animated`：内容页允许轻入场、手势滚动、图像交接、轻量按压反馈；禁止同步的循环粒子、HUD、强视差与自动轮播。

- **Background:** 只有静态 canonical Morning Lake BG_DATA，不使用循环视频作页面背景，也不能让湖景因月份切换而变成新地点/新光源。
- **Month switch:** 网格按数据瞬时重排，控件可有温和淡入，具体毫秒值为后续审核项，不得凭高保 PNG 假定“视频已冻结”。
- **Date selection:** 选中框/列表变化以内容稳定和触点保持为第一目标；不使真实鱼获照片飞入或缩放。
- **Year selection:** 4×3月份卡即时响应，下面的日期列表原位刷新；不添加数字计数滚动动画。
- **Reduce Motion:** 装饰动效消失；保留点击、日期转换、滚动、选中态的全部语义。
- **Video:** 目前没有这张 Record Date 页的独立冻结视频分镜或视频母资产。本页两张成果是 PNG 静态高保；未来需要独立的 Motion/Video 审核流程，不可把未制作的内容标成已发布。
