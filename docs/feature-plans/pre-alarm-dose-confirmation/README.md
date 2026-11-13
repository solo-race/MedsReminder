# 时间未到确认（提前服药确认）实施计划

版本：1.0；编制日期：2026-11-13（北京时间）。
状态：独立审阅 PASS（deepseek，2026-11-13；两轮修订后）；P1–P4 已实现，验证结果见第 8 节。
仓库：`solo-race/MedsReminder`。
工作分支：`feat/confirmation-before-alarm-ui-exp`。
基线：`fe0e23c`（`master` `bbf8e9a` 之后仅新增审阅归档文档）。

## 1. 目标与范围

修复实际使用中发现的问题：服药时间未到时，在药物详情页点击“标记为已服用”或“跳过本次”会立即写入决定，不要求再次确认；同时该按钮与到点后的正常触发使用相同的 `primary` 颜色，只能靠文字区分。本计划交付以下行为：

1. 时间未到（`now < scheduledFor`）时，任何提前的 Taken/Skipped 先弹出确认窗口；到点及以后（`now >= scheduledFor`）保持现有“一次点击即记录”。
2. 提前状态下，详情页的操作按钮和确认窗口的确认按钮使用区别于正常触发的颜色（主题 `tertiary` 暖色）；正常触发继续使用 `primary`。颜色之外同时保留文字提示，避免颜色成为唯一信号。
3. 新增一个可单元测试的展示状态机，统一“前端要素 + 提醒状态”的分类：加载、过期、无可用、已决定、可操作（提前/到点）。
4. 提前提醒（PRE，T−5 分钟）的通知快捷操作不再直接写入决定，而是打开详情并进入同一个确认窗口；到点通知（DUE）保持现有直接操作。两条入口共用同一套时间判定，避免出现“通知已到点、页面仍算提前”的分歧。

### 范围内文件

- `app/src/main/java/com/example/medicationreminder/ui/MedicationDetailScreen.kt`
- `app/src/main/java/com/example/medicationreminder/ui/theme/Theme.kt`
- `app/src/main/java/com/example/medicationreminder/ui/MedicationViewModel.kt`
- `app/src/main/java/com/example/medicationreminder/ui/MedicationApp.kt`
- `app/src/main/java/com/example/medicationreminder/MainActivity.kt`
- `app/src/main/java/com/example/medicationreminder/reminders/ReminderNotifications.kt`
- `app/src/main/res/values/strings.xml` 与 `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/test/java/com/example/medicationreminder/ui/` 与 `app/src/test/java/com/example/medicationreminder/reminders/` 下的针对性测试

### 非目标

- 不改变“允许在提醒时间前记录剂量决定”的业务语义；本计划只增加确认与视觉区分。
- 不新增 Room 字段或迁移，不改变 AlarmManager 的 PRE/DUE 阶段、pending 持久化、通知渠道、声音或去重规则。
- 不重构 Home、History、Settings 或药物编辑器。
- 不引入新的全局设置项或用户偏好。

## 2. 已核实的基线与必须修改的原因

路径以 `app/src/main/java/com/example/medicationreminder/` 为代码根目录。

| 已有实现 | 已确认行为 | 本次处理 |
| --- | --- | --- |
| `ui/MedicationDetailScreen.kt` 231–236 行 | 标题按“提醒/未确认/下次”三选一，时间比较使用组合时冻结的 `now` | 由新状态机派生标题，并用每秒刷新的 `displayNow` 判定提前/到点 |
| `ui/MedicationDetailScreen.kt` 252 行 | 倒计时使用 `displayNow`，与标题的时间基准不一致 | 统一使用 `displayNow`，页面跨过计划时刻时按钮与提示自动切换 |
| `ui/MedicationDetailScreen.kt` 268–284 行 | “标记为已服用”为 `primary` `Button`，“跳过本次”为 `OutlinedButton`；点击直接调用 `viewModel.decideDose` | 提前状态改用 `tertiary`；点击先打开确认窗口，确认后才调用同一 `decideDose` |
| `ui/theme/Theme.kt` | 自定义 `ReminderColors` 未声明 `tertiary`，当前也未使用 `tertiary` | 显式增加暖色 `tertiary`/`onTertiary`，确保深色主题下与浅蓝 `primary` 明显区分 |
| `reminders/ReminderNotifications.kt` 75–95 行 | PRE 与 DUE 都添加 Taken/Skipped 广播操作，点击立即写入 | PRE 操作改为打开详情的 Activity PendingIntent，并携带请求状态；DUE 保持广播操作 |
| `reminders/ReminderNotifications.kt` 140–152 行 | 通知操作是 `PendingIntent.getBroadcast`，不经过 UI | 新增提前确认用的 `PendingIntent.getActivity`，复用现有 occurrence/zone extras |
| `MainActivity.kt`、`ui/MedicationViewModel.kt` 165–170 行 | 通知入口解析 `ReminderEntry`，由 `reminderEntryOpened()` 一次性消费 | 入口增加可选的“提前确认请求（occurrence + status）”，由详情页消费后清除 |
| `ui/HomeDetailOccurrence.kt` 44–59 行 | `occurrenceClock()` 在 STARTED 状态每秒更新，并处理跨午夜 | 直接复用，不新增计时器或后台服务 |

当前没有详情页 `AlertDialog`；应用内其他页面已有 Material 3 对话框用法，可沿用同一模式。提前决定仍是现有的事务性 `DoseDecisionUseCase`，确认窗口只改变入口，不复制持久化逻辑。

## 3. 新状态机

状态只依赖“当前展示数据 + 当前时刻”，不引入新的持久化状态：

```kotlin
internal enum class DoseConfirmationPhase { EARLY, DUE }

internal fun doseConfirmationPhase(occurrence: DoseOccurrence, now: Instant): DoseConfirmationPhase =
    if (now.isBefore(occurrence.scheduledFor)) DoseConfirmationPhase.EARLY else DoseConfirmationPhase.DUE

internal sealed interface DetailDosePresentation {
    data object Loading : DetailDosePresentation
    data object Stale : DetailDosePresentation
    data object Unavailable : DetailDosePresentation
    data class Decided(val status: DoseStatus?) : DetailDosePresentation
    data class Actionable(
        val occurrence: DoseOccurrence,
        val confirmation: DoseConfirmationPhase,
    ) : DetailDosePresentation
}

internal enum class DetailDoseHeading { REMINDER, UNCONFIRMED, NEXT }
```

标题仍以 `explicitOccurrence` 和 occurrence 是否为过去时间为输入，不能只由 `DetailDosePresentation` 推断。为保持现有行为（`MedicationDetailScreen.kt` 231–236 行），规则固定为：`explicitOccurrence != null` → `REMINDER`；否则 `occurrence != null && now >= scheduledFor` → `UNCONFIRMED`（包括已决定/过期），其余 → `NEXT`。实现时保留 `explicitOccurrence` 作为独立参数，并以 `detailDoseHeading(explicitOccurrence, occurrence, now)` 单元测试；已决定和过期的过去 occurrence 仍为 `UNCONFIRMED`，不得翻成 `NEXT`。

| 输入 | 状态 | 前端要素 |
| --- | --- | --- |
| `doseState == null` | `Loading` | “正在检查服药信息…” |
| `doseState.stale` | `Stale` | 过期提示，无操作按钮 |
| `occurrence == null` | `Unavailable` | 无可用服药提示 |
| `decided == true` | `Decided(status)` | 状态条，无操作按钮 |
| 可操作且 `now < scheduledFor` | `Actionable(EARLY)` | 暖色操作按钮、提前说明、点击后确认窗口 |
| 可操作且 `now >= scheduledFor` | `Actionable(DUE)` | 现有 `primary` 按钮、一次点击记录 |

边界规则：`scheduledFor == now` 属于 `DUE`；每个秒级时钟刷新都重新派生状态，因此页面停留在提前状态跨过计划时刻后会自动变为正常触发；确认窗口已打开时，其确认按钮也按同一 `displayNow` 着色。

## 4. 确认窗口

- 提前点击“标记为已服用”或“跳过本次”时，不立即写入；弹出 `AlertDialog`，标题区分 Taken/Skipped，正文包含计划时间与“尚未到时间”的说明。
- 确认按钮文案为“仍然记录”，使用 `tertiary` 填充色；取消按钮复用现有 `cancel` 字符串，不写入决定。
- 确认时沿用点击时的 `DetailDoseState` 与 `ReminderContext`，调用现有 `viewModel.decideDose`；已有的 key/context 校验与 `applyDecision` 逻辑保持不变。
- 通知 PRE 快捷操作带入的请求状态在详情页加载后直接打开同一窗口；若该 occurrence 已过期、已决定或与当前详情不匹配，则忽略并清除请求。
- 到点状态点击不弹窗，保持现有一次点击行为。
- 提前说明放在操作区：例如“计划时间 %1$s，现在确认将记为提前服药。”颜色之外始终有文字说明。
- 窗口关闭条件固定为：用户确认、用户取消/返回、当前 occurrence 改变、计划 key 改变、或 `applyDecision` 收到 `AlreadyDecided`/`StaleOccurrence`。通知在窗口打开期间被 DUE 广播处理时，窗口自动关闭并展示已决定/过期状态，不重复写入。

## 5. 通知入口

- PRE 通知的 Taken/Skipped 操作改为 `PendingIntent.getActivity`，目标为 `MainActivity`，携带现有 `EXTRA_MEDICATION_ID`、`EXTRA_DOSE_TIME_ID`、`EXTRA_SCHEDULED_FOR`、`EXTRA_ZONE_ID`、`EXTRA_MODERN_NOTIFICATION`，并新增请求状态 extra。
- DUE 通知继续使用 `ReminderActionReceiver` 的广播操作，点击立即记录。
- `MainActivity` 解析请求状态并放入 `ReminderEntry`；`MedicationViewModel` 以 `PendingEarlyDecision(occurrence, status)` 保存一次性请求，详情页消费或关闭窗口后清除，Home 打开详情时同时清除。
- 新增请求的 identity 必须与现有 PendingIntent 区分。已占用 offset：0（alarm）、1（内容入口）、2/3（广播 Taken/Skipped）、4/5（旅行）、6（dismiss）、7（`SHOW_INTENT_OFFSET` 系统显示入口）、100/200（通知 id）。因此 Taken 使用 `ReminderScheduler.requestCode(doseTimeId, 8)`、action `EARLY_RECORD.TAKEN.<occurrenceTag>`、data `meds://early-confirm/<doseTimeId>/<scheduledFor>/TAKEN`；Skipped 使用 offset `9`、`EARLY_RECORD.SKIPPED.<occurrenceTag>` 与对应的 `SKIPPED` data。两者均使用 `FLAG_UPDATE_CURRENT or immutableFlag()`。
- 不改变通知的投递时间、渠道、声音、持续性和影响 identity 的去重键。
- `PendingEarlyDecision` 的生命周期与现有 `_reminderOccurrence` 一致：保存在 ViewModel，Activity 重建后仍存在；首次展示不消费，只有确认、取消、Home 打开详情、occurrence 改变、计划 key 改变或 stale/decided 不匹配时才清除。Dialog 的打开状态由该 ViewModel 值派生，不使用会随组合销毁的局部布尔值作为唯一来源；已决定的 occurrence 即使因冷启动重新收到同一 extra，也不会再次弹窗。

## 6. 文案

新增英文与简体中文资源，命名固定、语义明确；不使用拼接句：

- `detail_early_notice`：计划时间与“现在确认将记为提前服药”。
- `early_confirm_taken_title`、`early_confirm_skip_title`：两种操作各自的确认标题。
- `early_confirm_message`：包含计划时间的确认正文。
- `early_confirm_action`：确认按钮“仍然记录”。
- `notification_review`：仅在需要时用于 PRE 通知的操作文案（若保留双操作则复用现有 Taken/Skipped 文案，并在计划审阅时确定）。

现有 `cancel` 与 `status_taken`/`status_skipped` 继续复用。

## 7. 实施顺序

| 阶段 | 内容 | 完成判据 |
| --- | --- | --- |
| P1 | 新增 `doseConfirmationPhase`、`DetailDosePresentation` 及纯函数派生；补齐边界测试 | T−1ms/EARLY、T/DUE、T+1ms/DUE，以及 loading/stale/unavailable/decided 映射全部通过 |
| P2 | 详情页接入状态机：统一 `displayNow`、暖色提前按钮、提前说明、确认窗口、英文/中文文案 | 提前点击不写库，确认后才写入；取消不写入；到点仍一次点击；除下述必须更新的源码契约测试外，既有测试保持通过 |
| P3 | PRE 通知操作改为 Activity 入口，MainActivity/ViewModel/Detail 传递并消费一次性确认请求；DUE 保持广播操作 | PRE 操作不再直接写库；DUE 行为不变；入口解析与状态清除有针对性测试 |
| P4 | 针对性测试、完整单测、lint、debug 构建、文档索引更新 | 完整套件、`lintDebug` 0 错误、`assembleDebug` 成功；实机项明确列出待验证 |

每个阶段完成后先跑该阶段相关测试；P4 才运行完整验证。实施阶段不创建 PR、不合并、不打标签、不发布。

## 8. 验证

针对性单元测试：

- 状态机：提前/到点边界、缺失 occurrence、stale、已决定。
- 通知入口：PRE 为 Activity PendingIntent 且携带状态，DUE 仍为广播；请求状态解析与非法值忽略。
- `MedicationDetailNavigationContractTest` 目前直接断言 `MedicationDetailScreen.kt` 源码包含 `viewModel.decideDose(occurrence, DoseStatus.TAKEN` 与 `...SKIPPED`。实现后存在两个调用位置：DUE 的直接点击路径和 EARLY 确认窗口的确认分支；两处都保留以局部变量 `occurrence` 为实参的同一调用形式，使断言继续验证“Taken/Skipped 走同一 ViewModel 决定路径”。若变量命名或所在位置调整，同步更新该测试，但不得删除断言或改成弱化的源码文本匹配。
- 现有 `MedicationDetailLogicTest`、`MainActivityReminderEntryTest`、`ReminderActionOccurrenceParsingTest` 保持通过；新增旋转/重建后窗口仍在、确认/取消后不再重开的 ViewModel 用例。

完整验证（Windows 工作站，按仓库约定显式设置环境）：

```powershell
$env:JAVA_HOME = 'D:\AndroidStudio\jbr'
$env:GRADLE_USER_HOME = 'C:\Users\tiany\.gradle'
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest assembleDebug lintDebug -ProbolectricDependencyDir=C:\Users\tiany\.m2\repository\org\robolectric\android-all-instrumented\15-robolectric-13954326-i7 --no-daemon --offline --console=plain
```

实机待验证项：深色主题下提前/到点颜色对比、确认窗口在旋转与返回后的状态、PRE 通知操作打开详情后的窗口、DUE 通知一次点击、提前记录后倒计时与状态条刷新。

实现验证（2026-11-13，Windows 工作站）：

- 本机沙箱在 Kotlin/FUS 遥测访问 `dc.services.visualstudio.com` 时会终止 Gradle 客户端。使用 `kotlin.internal.collectFUSMetrics=false`、`kotlin.compiler.execution.strategy=in-process`，并通过 `JAVA_TOOL_OPTIONS` 传入同一 FUS 设置后完成编译。Robolectric 临时目录改用较短的 workspace 路径，避免 Windows MAX_PATH 造成 `SQLITE_CANTOPEN`。
- 已编译的完整单测套件直接通过 JUnitCore 运行：**207 tests, 0 failures**，包含新增状态机、ViewModel 一次性请求、PRE/DUE 通知操作类型和既有 Room 迁移测试。
- `assembleDebug`：**BUILD SUCCESSFUL**，产物 `app/build/outputs/apk/debug/app-debug.apk`。
- `lintDebug`：**BUILD SUCCESSFUL，0 errors / 17 warnings / 1 note**；警告均为既有类别（`UseKtx`、`UnusedResources`、`InlinedApi` 等），新增资源未被标记未使用。须将工作站代理作为 JVM 属性传入（`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=<port>`），JVM 不读取 `https_proxy` 环境变量。上述实机项仍未验收。

## 9. 风险与边界

- 提前决定仍会写入历史，统计口径不变；本计划不改变剂量汇总规则。
- 颜色对比需在深色主题与常见色觉差异下检查；文字说明与确认窗口承担主要语义，颜色只做辅助区分。
- 通知入口新增 Activity PendingIntent，需确认 Android 12+ 通知操作启动 Activity 的行为；不新增前台服务或全屏 Intent。
- 若独立审阅认为 PRE 通知保持直接操作更合适，可仅保留预览/查看入口；该取舍在本计划中已显式列出，不在实施中临时扩大范围。

## 10. 审阅依据

- `AGENTS.md`、`app/AGENTS.md`、`docs/review/README.md` 及其 Latest review、`docs/architecture/README.md`。
- 基线代码：`ui/MedicationDetailScreen.kt`、`ui/MedicationViewModel.kt`、`ui/HomeDetailOccurrence.kt`、`ui/theme/Theme.kt`、`MainActivity.kt`、`reminders/ReminderNotifications.kt`、`reminders/ReminderScheduler.kt`。
- 既有计划：`docs/feature-plans/medication-detail-dose-actions/README.md`（提前服药决定的既有契约）与 `docs/feature-plans/interval-reminders-dose-summary/README.md`（PRE/DUE 阶段与通知身份）。
