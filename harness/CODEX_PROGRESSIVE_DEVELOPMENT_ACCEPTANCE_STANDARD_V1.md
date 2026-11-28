# YuJian_App｜CodeX 渐进式研发与验收执行规范 V1.0

**状态：FROZEN｜正式冻结**  
**规范类型：跨 Work 执行约束（Cross-cutting Work Execution Standard）**  
**适用范围：** `pan277942135/Yujian_App` 后续所有 CodeX Work、源码修复、Runtime 集成、CI 验收、APK 构建和交付任务。  
**生效方式：** 由 `AGENTS.md` 启动指引和 `CONTROL_PLANE_REGISTRY.yaml` 六层控制面引用；产品分支同步 canonical control plane 后生效。

## 1. 目的与权威边界

本规范用于提高真实业务推进速度、测试可信度和故障收敛效率。它采用渐进式改进，不要求先完成一项长期的测试体系重构项目。

本规范是跨 Work 的执行约束，不是第二个控制面入口，不替代：

- `CONTROL_PLANE_REGISTRY.yaml` 的唯一入口与六层权威解析；
- `harness/PHASE_TASK_STATE_MACHINE.yaml` 的任务状态、依赖和重试上限；
- `harness/STATE_UPDATE_PROTOCOL.md` 的状态、证据和检查点协议；
- 已冻结的产品、识别、模型、26KF、Runtime、Result、Evidence 合同。

发生冲突时必须停止并按 canonical main 控制面和对应领域冻结合同解决，不得自行降低验收标准。

## 2. 六项冻结原则

### R01｜渐进式改进，禁止先行大规模重构

1. 不另行启动长期的“自动化研发与验收体系重构 V1”项目作为业务推进前置条件。
2. 不要求完整五层流水线全部建设完成后才允许推进识鱼业务。
3. 采用渐进式、绞杀式改进：只解决当前已证实、阻塞当前目标的问题。
4. 测试基础设施仅做当前必要的修复和增强，不为追求架构完整性扩大范围。
5. 优先获得可信的真实业务运行证据；基础设施改进与业务闭环并行推进，但不得互相掩盖失败。

### R02｜L0 最小化，模拟器健康检查独立

**L0 只执行静态、宿主环境、配置和资源预检，不启动 AVD，不运行 AndroidTest。**

L0 检查范围：

- JDK、Gradle Wrapper、Android SDK、Build Tools 版本与可用性；
- Runner 类型、KVM 可用性、宿主 CPU、可用内存、磁盘空间；
- API Level、ABI、System Image、AVD 和 Action 输入配置；
- 必需依赖、环境变量、脚本入口、权限和文件路径；
- AVD CPU、RAM、VM Heap 配置值及其单位/语义。

L0 目标耗时为几十秒至 1–2 分钟；这是优化目标，不是对 GitHub Runner 调度或外部依赖下载的 SLA。发现明确不满足项时应在昂贵构建前失败并输出具体字段。

**Emulator Health Gate 独立于 L0：**

- 创建并启动指定 AVD；
- 等待 `sys.boot_completed=1`；
- 检查唯一 ADB 设备、设备状态和 Instrumentation 最小启动能力；
- 检查来宾实际 CPU、RAM；
- 检查 AVD 的 `vm.heapSize` 配置和应用进程实际可用 VM Heap；
- 记录 System Image 的运行时属性，不能把不同层级、不同语义的属性混为同一个验收值。

**Heap 语义特别约束：**

- AVD 的 `vm.heapSize` 是 AVD 配置中的 VM Heap 设置，应按 AVD 配置和应用进程实际可用 Heap 分别核验。
- 来宾系统的 `dalvik.vm.heapsize` 是独立运行时属性；其值可能由 API/System Image 决定，不得未经合同定义就要求它必须与 `vm.heapSize` 数值相等。
- `dalvik.vm.heapsize` 不等于 Native Heap，也不能单独证明应用进程的实际最大 Heap。
- 不允许通过启动后 `setprop` 伪造启动配置验收结果。

### R03｜生产业务闭环优先，基础设施按需修复

YuJian 生产识鱼主线保持：

`EXIF/Photo Identity → Classifier → SlimSAM → Mask → Contour → Fish Path → AUTO → Gold → Glyph → Result`

1. 不因测试体系尚未完备而长期暂停业务闭环。
2. 基础设施故障、测试框架故障、模型故障和业务逻辑故障必须分别分类。
3. 修复基础设施时，不得顺带扩大识别 Runtime 架构修改。
4. 修复业务时，不得用 CI、ADB 或测试框架问题掩盖业务缺陷。
5. 已具备运行条件的测试应尽快执行并保留真实业务事件与结果。
6. 上游生产者未生成可信数据时，不得通过修改下游消费者、使用模拟数据或跳过 Gate 伪造闭环。

### R04｜自动修复熔断机制

1. 自动修复必须遵守 `harness/PHASE_TASK_STATE_MACHINE.yaml` 与 `tools/work_execution_v2/failure_gate.py` 的既有预算和检查流程。
2. 既有硬上限保持不变：每个 failing gate 最多 2 次自动修复、总尝试次数最多 3 次（初始尝试 + 最多 2 次修复）；本规范不增加预算。
3. 同一失败类别/症状连续两轮没有可验证进步时，提前停止自动试探，不必耗尽剩余预算。
4. 单个问题不得超过既有三次总尝试上限；不得通过更换分支、Task ID 或 source SHA 重置同一失败的预算。
5. 发现跨层问题时，暂停当前层修改并重新分类。例如：业务修复发现 ADB/AVD 不可用，应先停止业务试探；Heap 排障发现测试报告路径错误，应转为测试框架问题。
6. 熔断后必须输出：已确认事实、失败证据、根因（CONFIRMED/PARTIAL/HYPOTHESIS）、已尝试修复、无进步原因、剩余风险、最小下一步方案及需要的人类决策。
7. 非独立的新假设不得被包装成“新根因”以规避 failure gate。

### R05｜SlimSAM 性能与 Native Memory 提前止血

以下是当前阶段的诊断红线，不代表最终产品性能预算：

| 指标 | 冻结要求 | 触发动作 |
|---|---|---|
| 模型 Attempt Deadline | 60 秒，不得延长规避 | 超时即记录真实失败阶段 |
| Native Heap | 峰值达到或超过 2GB | 触发 P0 内存根因调查 |
| LMK / OOM / Native Crash | 不允许发生 | 测试失败、保留完整现场 |
| ONNX Session | 必须真实 READY | 未 READY 禁止进入推理 |
| Vision Encoder | 必须记录分阶段耗时 | 缺阶段数据不得声称性能通过 |
| Embedding | 记录生成、缓存命中、身份和生命周期 | 检查重复计算及缓存失效 |
| 临时内存 | 记录分配、峰值、释放和持有者 | 检查 Bitmap、Tensor、DirectBuffer、Native Buffer 泄漏/重复持有 |

测量必须区分 Dalvik/Java Heap、Native Heap、PSS、RSS 和可获得的 GPU 内存；不得将不同内存域相加后冒充单一 Heap 指标。

达到 2GB Native Heap 是 P0 调查触发线，不是通过增加 Runner/AVD RAM 即可关闭的问题。必须沿 ONNX Session、Tensor、Embedding、Mask、Bitmap、DirectBuffer 和线程生命周期定位真实分配来源。

禁止通过延长 60 秒 Deadline、降低冻结输入分辨率、替换模型、降低识别阈值或跳过 Mask/Path 来规避失败。任何模型或输入规格变更必须走对应冻结合同的正式变更流程。

### R06｜每轮必须有可验证进步与可信证据

每轮 Work 必须：

1. 明确一个主要目标、修改范围和禁止修改范围。
2. 记录 canonical control-plane revision、branch、Exact-HEAD 和父 Commit。
3. 先分析真实失败证据，再实施最小修复。
4. 只运行与改动范围相匹配的测试；需要完整验收时再运行完整链路。
5. 明确报告相对上一轮的可验证进步；没有进步不得以增加代码、日志或测试数量冒充进展。
6. 结果必须绑定实际 source SHA、workflow run、设备/Runner、测试套件和构件。
7. 每个测试阶段独立记录开始/结束时间、退出码、断言、业务事件和失败分类。
8. 保留 FIRST_FAILURE；后续汇总不得覆盖首个失败原因。
9. Runtime 已启动时必须生成机器可读终态报告（优先 `runtime-result.json`）；若在 Runtime 启动前失败，必须生成明确的 pre-runtime failure summary，并记录未生成 runtime 报告的原因。
10. 失败时尽可能保存 Logcat、模型耗时、内存、关键业务事件、截图及其他可用 Evidence；失败证据不得因后续重试成功而删除。
11. 无真实业务事件、生产推理和必要断言，不得报告业务 PASS；不得用 mock/stub/固定输出替代生产推理。
12. Build PASS、APK 构件存在、Unit PASS 均不能替代 Runtime、Journey 或 Evidence PASS。
13. 同一 source SHA 的环境/Runner 重试应复用已经校验的同一 APK 构件；只有 source SHA 改变、构件缺失/损坏或构建输入变化时才重新构建。复用时必须再次核验构件 provenance 和 SHA256。
14. Release 只能由同一 Exact-HEAD 的必需构建、Runtime、业务和 Evidence Gate 全部通过后触发；未验收 APK 不得作为最终交付。

## 3. 推荐执行顺序（渐进式，不要求一次性建成）

1. **L0 Static Preflight：** 静态工具链、宿主资源、配置与依赖检查。
2. **L1 Targeted Validation：** 受影响模块单测、静态检查、脚本测试和必要编译。
3. **L2 Build：** 对当前 Exact-HEAD 构建 APK/AndroidTest，记录并归档构件。
4. **Emulator Health Gate：** 独立启动 AVD、检查 Boot/ADB/资源/最小 Instrumentation。
5. **L3 Runtime / Business：** 复用同 SHA APK 执行 A/B/C、模型、Mask、Path、AUTO、Result 和 Evidence。
6. **L4 Real Device：** 对 GPU/NPU、真实推理耗时、Native Memory、LMK、视觉效果和持续运行进行真机验收。

该顺序是推荐的渐进式门禁，不要求先建设一个全新的大型流水线。可以根据当前 Work 的修改范围复用已有 Gate，但不得跳过该任务实际要求的必需验收。

## 4. Work 报告最小格式

每轮至少报告：

```text
WORK_ID / TASK_ID:
CONTROL_PLANE_REVISION:
BRANCH / SOURCE_SHA / PARENT_SHA:
PRIMARY_GOAL:
CHANGED_PATHS / FORBIDDEN_PATHS:
FIRST_FAILURE:
FAILURE_CLASS:
ROOT_CAUSE: CONFIRMED | PARTIAL | HYPOTHESIS
FIX_AND_REASON:
TESTS: command / count / pass / fail / skipped
EMULATOR: AVD / API / CPU / RAM / vm.heapSize / observed runtime properties
MODEL: Classifier / Session READY / Encoder stage timings / Attempt duration
MEMORY: Dalvik Heap / Native Heap / PSS / RSS / GPU (available metrics)
BUSINESS: EXIF / Classifier / Mask / Contour / Fish Path / AUTO / Result
EVIDENCE: report / Logcat / screenshots / ZIP / hashes
PROGRESS_VS_PREVIOUS_ATTEMPT:
RETRY_BUDGET: initial / repairs used / remaining
STATUS: PASS | FAIL | BLOCKED | NOT_RUN | NOT_CONFIRMED
NEXT_ACTION:
```

未执行的项目必须标记 `NOT_RUN`，被环境阻塞的项目必须标记 `BLOCKED_<reason>`。不得推断或补写不存在的测试结果。

## 5. 生效与变更

- 本规范对 YuJian_App 后续所有 Work 生效，包括 PR #346 后续工作和新建产品分支。
- 规范由 `main` 上的 canonical Control Plane 引用；产品分支必须按既有 Control Plane Sync 和 Admission 流程同步后执行。
- 本规范不改变当前 PR #346 的业务范围，不授权修改已冻结产品/模型/26KF 合同，也不改变当前 Work 的 Exact-HEAD 验收要求。
- 任何修改本规范的提议必须走 main-first Control Plane Change；不得在产品分支创建竞争版本。
- 本规范不要求为了“规范落地”中断正在进行的有效 Work。

**最终原则：研发体系服务于产品交付；每轮 Work 必须推进真实业务，或让阻塞问题更准确、可复现、可定位。不得为了测试体系建设而长期延迟生产识鱼闭环。**
