# YUJIAN_MAIN_BASELINE_RECOVERY_BOOTSTRAP_V1

Task ID: `YUJIAN_MAIN_BASELINE_RECOVERY_BOOTSTRAP_V1`

## One-time Main baseline recovery admission

This is the repository-maintainer-authorized, one-time bootstrap path for the
`recovery/main-baseline-v1` pull request. It exists solely to place the frozen
Empty Home V2.2 task contract and its verified implementation into Main in the
same candidate, thereby resolving the otherwise circular Main-first gate.

The exact Android app file set is:

- `app/src/main/java/com/yujian/ai/ui/home/EmptyHomeMotion.kt`
- `app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt`
- `app/src/test/java/com/yujian/ai/ui/home/EmptyHomeMotionTest.kt` (required regression coverage)

No other Android app path is authorized. This does not waive any verifier or
CI check. The recovery must retain the frozen V2.2 anchor and layer contracts,
the single bobber body, its independent low-alpha reflection, the canonical
rod-to-bobber Bézier, and the static/runtime Empty Home checks.

It must not change the frozen V2.2 product contracts. It must not promote the W5 candidate geometry. This bootstrap exception is not reusable by ordinary
product, design, or control-plane pull requests.
