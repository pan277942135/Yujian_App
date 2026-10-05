# Copy / Behavior Spec

## Forgot Password
- Account: “输入注册时使用的账号，我们会通过已绑定的联系方式验证身份。”
- Verification: 6-digit code; resend 60 s; code validity target 10 min.
- New password: minimum 6 characters unless backend rule is stricter.
- Success: invalidate old sessions where supported.

## AI model improvement — enable
“仅会使用照片中框选出的鱼体部分和你确认的鱼种，用于训练和改进鱼种识别模型。”
Buttons: “暂不开启” / “允许用于模型改进”

## AI model improvement — disable
“关闭后，新的纠错鱼体和你确认的鱼种将不再用于训练鱼种识别模型。”
Must state that recognition / fish-record save / correction are unaffected.

## Location
“只有当你主动选择‘使用当前位置’时，渔见才会获取你的位置，用于为当前鱼获添加地点。”
Denial does not affect recognition or fish-record save.

## Export
Include user-facing business data only. Do not export password hashes, tokens, secrets, internal risk fields or internal training IDs.

## Delete Account
Final confirmation is destructive; use danger styling only on the final action.
