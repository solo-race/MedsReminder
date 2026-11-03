# 周期间隔提醒与剂量汇总实施计划

版本：1.1；编制及基线修订日期：2026-11-03（北京时间）。
状态：主控已转达基线计划独立审阅 PASS（无阻塞）；P1 实现已交付，状态 A → B，等待独立阶段审阅及 Room 运行验证。P2–P4 尚未实施。
仓库：`solo-race/MedsReminder`。
工作分支：`codex/feature-interval-reminders-dose-summary`。
当前实施基线：合并提交 `2b6822ebb098adabba045415b2d460543e25d14b`，已整合 `master` 的 `de2f6b674c64565f95883e704341c6c599049214`，包含自启动及待服选择修复 `2cbdad2`。分支最初从 `codex/feature-medication-detail-dose-actions` 的 `2e05c90` 创建，原计划提交为 `9c41dda`；这些仅为创建历史，不再作为当前实施基线。后续不重建或覆盖已有历史。

## 1. 目标与范围

实现以下五项功能，并保留现有定时服药、药物详情、Taken/Skipped、通知入口、精确闹钟权限处理和中英文界面：

1. 增加“按间隔”模式，以首次服药日期时间为起点，每隔指定时长生成一次计划服药时间。
2. 在服药时间前5分钟提醒，通知及应用内显示倒计时。
3. 设置中增加全局“响铃提示”开关。
4. 通知持续对应同一次未处理服药，直到 Taken 或 Skipped；查看应用或通知不算处理。
5. 记录页顶部增加“剂量汇总”入口，打开独立汇总页面，按药物和剂量单位汇总。

最初计划轮仅交付计划及文档；本轮按授权只实施 P1，完成后停止并提交一次。不创建 PR、不合并、不打标签、不发布。实施复用本项目模块，不新建调度服务、计时后台服务、统计数据库、通用任务框架或云端能力。

## 2. 已核实的基线与必须修改的原因

以下路径以 `app/src/main/java/com/example/medicationreminder/` 为代码根目录。

| 已有实现 | 已确认行为/问题 | 本次处理 |
| --- | --- | --- |
| `domain/scheduling/NextDoseCalculator.kt` | 只按星期和每日时刻计算 | 增加间隔模式分支，继续使用同一个计算入口 |
| `data/local/Daos.kt`、`data/repository/MedicationRepository.kt` | 服药决策按 slot + 本地日期去重 | 间隔模式按 slot + scheduledFor 去重，否则同日第二次会被吞掉；固定时刻保留现有逻辑 |
| `DoseEventEntity` | 已有唯一索引 `(doseTimeId, scheduledForEpochMillis)` | 直接复用，不另建去重框架 |
| `ReminderNotifications.kt` | 通知及部分 PendingIntent 只按 slot 标识 | 改为每次 occurrence 标识，防止新一轮覆盖旧提醒或操作删错通知 |
| `ReminderDoseDecisionEffects.kt` | 处理后以旧 scheduledFor 安排后续 | 处理积压提醒时应从当前时刻重建未来 alarm，避免倒排过去时间 |
| `ui/HomeDetailOccurrence.kt`、`MedicationViewModel.kt`、`MedicationDetailScreen.kt` | 普通详情已优先选择计划时区当天最近的未决策到期项，再回退到最早未决策未来项；不合成前日积压 | 在此基础上增加持久 pending 最早优先；无 pending 时固定模式保留现有当天待服回退，不重做已有修复 |
| `MedicationApp.kt` 的 `HomeScreen / MedicationCard` | 首页排序及卡片仍按未来时刻展示，与普通详情的待服选择不同 | 统一卡片与详情当前 occurrence 的选择及倒计时，未来下一次可另列 |
| `MainActivity.kt`、`MedicationViewModel.kt`、`MedicationDetailScreen.kt` | 已有 `onNewIntent` / `singleTop`、显式通知 occurrence 优先、keyed 加载结果与状态隔离，以及统一 `atZone` 的 DST 校验 | 扩展 interval/pending 时保留这些修复及测试，不另建导航或状态框架 |
| `AutostartSettings.kt`、设置页手动引导 | OEM 页面打开失败已转为可恢复、可滚动的手动引导；通用设置为单独标明的动作，不以跳转结果判断授权 | 保留现有引导，不恢复 App Details 自动回退，不另建厂商权限框架 |
| `ReminderNotifications.kt` 的显示内容 | `VISIBILITY_PUBLIC`，正文和展开内容显示 dosage；没有 `publicVersion` | 保持当前 alias/通用标题和剂量显示行为，不声称已有锁屏脱敏，不在本 feature 新增脱敏功能 |
| `DoseEventDao.observeAllWithMedication()` | INNER JOIN 读取当前药物 dosageText，事件没有剂量快照 | 新事件保存当次剂量；不能用当前剂量乘历史次数 |
| `RoomMedicationRepository` | 决策时清理30天前事件；删除药物同时删其事件 | 汇总明确限制为保留记录，不宣称全历史累计；本次不改变删除/保留策略 |
| `HistoryScreen` | 空历史单独分支 | 汇总入口置于空/非空分支之前，零记录也可访问 |
| 最新 review（2026-10-24 23:33 SGT） | 重建自启动引导、当天待服选择、keyed 状态和 DST 修复；报告当时 135 项测试通过，实际 Compose/OEM/设备验收仍待执行 | 以当前源码确认已合并修复；历史测试与 APK 仅证明当时范围，不当作当前 feature 的验证 |
| 历史设备 review（2026-09-21 01:59 SGT） | ColorOS 禁止自启动时可阻止冷进程接收闹钟；允许自启动的对照通过 | 保留系统限制说明，不为此新建后台常驻或重试框架 |
| `app/build.gradle.kts` | 当前 `versionName = "1.0.0-beta.4"`、`versionCode = 4` | P4 才按最终基线递增版本；计划修订不改版本或源码 |

最新 review 的权威入口仍为 `docs/review/README.md`。该报告记录的是当时修复范围；本次以合并基线源码复核，不把历史 135 项测试、缓存输出或旧 APK 当作当前 feature 的新验证。历史报告不替代本轮 P1 检查；本轮实际结果见第8节及 `docs/memory.md`。

## 3. 明确的产品行为

### 3.1 周期间隔

在现有药物编辑页的计划区域加入模式选项：“固定时刻 / 按间隔”。旧药默认固定时刻，界面与行为不变。按间隔显示“首次日期、首次时间、间隔小时与分钟”；小时分钟组成正整数分钟时长，可输入超过24小时的间隔。禁止0或负间隔，只做有效值及时间计算不溢出的必要校验。

本版使用固定起点递推：
`scheduledFor(n) = anchorInstant + n × intervalMinutes`，`n >= 0`。
例如11月3日08:00起、间隔12小时：08:00 → 20:00 → 11月4日08:00。当天07:55为08:00预提醒，19:55为20:00预提醒。

Taken/Skipped 仅处理所选的一次，不将下一次改成“实际点击时间 + 间隔”。提前或迟服不漂移原序列，不自动推荐或调整用药间隔。该解释是本计划明确采用的实现口径。

间隔模式不同时使用星期筛选或多个每日时刻；隐藏这些控件并在保存时按模式校验，不能因隐藏的 times/weekdays 空值拒绝保存。首次时间按现有时区选择解释后保存为 epoch instant。时区变化只改变显示，不改变已经存下的间隔起点与绝对间隔；固定时刻模式继续现有时区逻辑。系统时间修改沿用现有 receiver 重建；不用24小时内展开的固定时刻冒充间隔算法。

保留一个真实持久化的 dose_times 行作为该间隔序列的稳定 slot，避免引入第二套 slot 模型。仅改药名、备注、照片或剂量时保留 slot 与 anchor；修改模式、anchor 或 interval 时创建新 slot，取消旧序列的未来 alarm 和未处理通知，旧决策记录保留至既定清理策略。明确变更计划/禁用/删除属于用户主动终止原提醒，不伪造为 Taken/Skipped。

### 3.2 提前5分钟、倒计时与到点

提前量固定5分钟，适用于固定时刻与间隔两种模式，不增加提前量设置。倒计时出现在首页药物卡、详情当前 occurrence 和预提醒通知。UI 显示距服药时刻的 hh:mm:ss；到点后显示“已到服药时间”及逾期时长，不显示负倒计时，不自动切换到下一次。

每个 slot 的未来调度复用 AlarmManager，区分 PRE（预提醒）和 DUE（到点）两个 alarm 身份。两者携带的 `scheduledFor` 都是真正服药时刻，不能把提前5分钟时间写进决策身份。PRE 到达仅建立/更新当前通知，并安排下一次 PRE，不触碰当前 DUE；DUE 更新同一通知并安排下一次 DUE，不覆盖已排好的 PRE。两个阶段分别按自己的触发时间递推，因此间隔小于5分钟时，下一次预提醒也能发生在本次到点之前。

重建时分别求最近未来 PRE 和 DUE；当前5分钟窗口内尚未处理、未登记pending的各次未来服药立即建立预提醒状态，已登记的只恢复。最小正整数分钟间隔使窗口最多包含5次，不遍历整个历史。已过的 PRE 不向 AlarmManager 注册。已有同一 pending 时只静默恢复。提前 Taken/Skipped 后，本次 PRE/DUE 都不得再提示。

UI 用生命周期感知的轻量定时刷新显示时间，页面不可见停止刷新；每秒不查库、不重调度 alarm。通知使用系统 chronometer（`setWhen(dueMillis)`、`setUsesChronometer(true)`、`setChronometerCountDown(true)`）；DUE 到达关闭倒计时并更新到点文案，不建后台每秒更新通知的服务。

### 3.3 响铃提示

复用 `AppPreferences` 增加全局 `reminderSoundEnabled`，默认 true，设置页可切换并持久化。

开：PRE、DUE 各提示一次系统通知声；关：两个阶段均无声，通知、倒计时、Taken/Skipped 功能照常。刷新倒计时、打开应用、恢复进程、重贴被滑掉的通知均不重复响。这里的“响铃”是一次提示声，不扩展成循环闹铃、贪睡或自选铃声。

保留现有有声通知 channel，新增一个静音 channel；按开关选择，不能尝试修改已创建 channel 的声音属性。静音发送同时显式禁止声音，避免用户改过静音 channel 后违背应用开关。开关变化对现存通知静默刷新；到点时用当时偏好。不增加独立振动设置；关闭响铃使用无声channel并显式静默发送，不承诺维持系统原来的振动表现。系统通知权限、系统 channel 静音及勿扰仍优先，不承诺绕过系统设置。

### 3.4 持久通知与当前 occurrence

同一次服药在 PRE、DUE、点击打开、进程恢复后都保持相同 `(doseTimeId, scheduledFor)`。Taken/Skipped 成功提交数据库后才能撤销对应通知；写入失败保留通知并显示既有失败反馈，不把失败当成功。

增加一张最小 `pending_reminders` 表，存储已进入提醒流程但未处理的 occurrence。现有 dose_events 仍仅表示已作出的决策，不把 PENDING 混入服药记录。新增表服务于现有 repository/reminder 链路，不新建独立服务模块。

状态路径为：未提醒 → PRE pending → DUE pending → Taken/Skipped 后移除 pending。直接收到 DUE 也可以建立 pending。发布通知前在事务内检查决策并保存 pending，阶段只允许PRE→DUE；重复投递不产生新的阶段提示声。发送时沿用既有发送前/后决策复核：若发送后发现同一次已经决策，立即取消该occurrence通知，避免检查与notify之间的竞争留下残留。恢复pending按当前时刻渲染PRE/DUE，不因存储仍为PRE而在到点后显示负倒计时；恢复本身静音。

继续使用 `setOngoing(true)`、`setAutoCancel(false)`，不设置自动超时移除。通知使用完整 occurrence 的 tag，例如 `dose:<slot>:<epoch>` 加固定整数 id；content/delete/action PendingIntent 的 action 或 data 也含完整 occurrence，不能只依赖 extras。同一 occurrence 到点更新原通知；下次服药到来时保留未处理旧通知，不覆盖。决策取消只针对该 occurrence；按 slot 清理仅用于删除/禁用/变更计划。

复用 `ReminderDismissReceiver` 静默重贴，复用 Application/ViewModel/SystemEventReceiver 的恢复入口读取 pending；pending 不在跨日或30天决策清理中删除。保留 `MissedReminderReposter` 的统一入口：恢复已保存 pending 不受“补发错过提醒”开关影响；对从未进入提醒流程的历史 occurrence，仍由该开关控制补发策略，本版只补最近一次合规逾期 occurrence，不扫描整段历史生成大量通知。补发后同样保存 pending。

首页与普通详情使用同一选择规则：明确通知传入的 occurrence 优先；否则选该药最早未处理的持久 pending。没有持久 pending 时，固定时刻模式复用 `homeDetailOccurrence`：先选计划时区当天最近的未决策到期项，再回退到未来最近未决策项，不合成前日积压；间隔模式回退到未来最近未决策项，未登记逾期项的补发仍按上述 `MissedReminderReposter` 规则处理。持久 pending 的“最早优先”与固定模式无 pending 时的“当天最近到期优先”是两个明确分支，不能互相替代。未来下一次可另行显示，但主操作始终作用于当前绑定的 occurrence。跨到点、旋转、重新进入、时区显示刷新都不能悄悄改变当前操作对象。处理成功后按上述顺序重新选择。

直接扩展现有 selector、显式通知入口和 keyed 状态，保留已合并的 DST 规则及自启动手动引导；不为已有状态修复重复建立导航、状态或权限框架。

仅修改名称/剂量/备注/照片时，不再使用现有“保存前取消所有通知”逻辑清掉 pending；数据库保存成功后才按变更类型更新调度与通知。保存失败不得清掉旧提醒。

持久性指应用不主动超时撤销并可恢复未处理状态。Android 14+ 允许部分 ongoing 通知被用户清除，强制停止、撤回通知权限和厂商阻止自启动也无法由普通应用保证通知常在；沿用现有恢复和权限提示，不增加绕过系统的方案。

### 3.5 剂量记录与汇总

复用现有药物编辑器，在 dosageText 说明旁增加用于统计的“每次剂量数值、单位”。保留自由文本说明；结构化值可空以兼容旧药，若填写则数值必须大于0且单位同时填写。支持小数（如0.5片），单位使用明确的存储值（如 tablet、capsule、mg、mL）并本地化显示，允许自定义单位但仅按相同单位分组。不自动从“25mg×2片”等说明推断，也不把片数换算成有效成分质量。

默认一次 Taken 表示服用了当时确认的每次剂量；本版不扩展临时改量、历史补录编辑、每时段不同剂量或药品规格换算。记录时在同一个决策事务保存剂量数值、单位和 dosageText 快照。SKIPPED 不贡献服用量。

旧事件的新增剂量字段保持 null，不用当前药物设置回填旧量。旧记录显示“历史剂量未记录”；汇总计入 Taken/Skipped 次数，并显示“X次已服记录缺少剂量，未计入剂量合计”。已有药物补齐结构化剂量后，仅新记录可统计；新事件缺结构化值时也明确未知。修改药物剂量不改旧快照。

记录页顶部提供常驻“剂量汇总”按钮，进入 `history/summary` 专门页面；明细列表仍保留在原记录页。返回恢复原记录页，不新增底部主导航项。零记录时汇总页显示空状态和0次，不显示未知量为0服用量。

汇总页面提供“今天 / 近7天 / 近30天”，默认近7天。今天从当前设备时区当天00:00到当前时刻；近7天和近30天分别为当前时刻向前7×24小时、30×24小时。按 `actionedAt`（用户确认服用时间）归属，不按原计划服药时间；明确界面注明“按确认时间统计”。界面可见时在跨日/恢复前台时重算窗口。

按 medicationId 分组，分单位列出 Taken 剂量合计、Taken 次数、Skipped 次数和未知剂量次数；相同名字不同药物不合并，不同单位不相加，不显示跨药物总剂量。例：同药0.5片 Taken两次、1片 Skipped一次，结果已服1片、已服2次、跳过1次；再有1次未知剂量 Taken，则已服3次、合计仍1片并标明1次未计量。

范围与现有保留策略一致，页面说明“仅统计保留记录；删除药物会删除其记录及汇总”。历史流和汇总均应用同一30天过滤，避免清理尚未运行时一个页面显示过期记录、另一个不显示。保留当前自动清理，不新建汇总持久表，不提供全历史总计。新快照让后续剂量修改不再改变旧记录显示；旧事件未知是数据事实，不是待猜测的迁移任务。

## 4. 数据与接口改动

统一使用 Room v3→v4 迁移，保留原 v1→v2→v3 链，禁止 destructive migration。P1 一次定义本 feature 必需字段，后续阶段不反复抬版本。

| 位置 | 最小结构变更 |
| --- | --- |
| medication_schedules | `scheduleMode TEXT NOT NULL DEFAULT 'FIXED_TIMES'`；`intervalMinutes INTEGER NULL`；`anchorEpochMillis INTEGER NULL` |
| medications | `doseAmount TEXT NULL`（规范十进制字符串）；`doseUnit TEXT NULL` |
| dose_events | `doseAmountSnapshot TEXT NULL`；`doseUnitSnapshot TEXT NULL`；`dosageTextSnapshot TEXT NULL` |
| pending_reminders | `doseTimeId INTEGER` + `scheduledForEpochMillis INTEGER` 复合主键；`medicationId INTEGER`、`zoneId TEXT`、`stage TEXT`（PRE/DUE）；关联 medication/slot 删除级联；同一记录 stage 不从 DUE 倒退到 PRE |
| AppPreferences | DataStore boolean `reminder_sound_enabled` 默认 true，不进 Room |

金额式十进制精度直接用 `BigDecimal` 做剂量求和，规范字符串存储，避免浮点把0.1+0.2显示成尾数误差；不引入新依赖。迁移不解析 dosageText。保留事件已有唯一索引及 logical-day 索引。

扩展现有 `MedicationDraft / MedicationSchedule / ScheduledDose / Medication / DoseEvent` 传递模式、间隔、剂量字段；DoseEvent 增加 medicationId 供汇总分组。现有 `DoseOccurrence` 的核心身份不变；zoneId 在间隔模式仅用于显示，不能因设备换区拒绝有效 interval occurrence。

统一现有“是否已处理”入口为按 occurrence 查询：固定模式走 logical-day，间隔模式走 exact scheduledFor；所有调用点（scheduler、receiver、reposter、ViewModel、decision）同时更新。间隔合法性验证为当前 slot 归属、enabled、scheduledFor >= anchor 且差值是 interval 的整数倍；固定模式的新候选继续原 weekday/time 验证。对于数据库中已经存在的pending，先验证药物/slot仍有效，再按pending保存的scheduledFor和zone处理；不能因设备换区导致旧提醒无法Taken/Skipped。固定模式的logical-day决策键使用pending原zone计算，显式变更序列/禁用/删除仍按既定规则清理。复用一个校验函数或模型方法，避免 Repository、ReminderReceiver、Detail 三份规则漂移。

`decideDose` 继续使用原 Room transaction：检查合法性与已处理状态 → 插入带剂量快照的事件 → 删除同 occurrence pending → 原30天已决策历史清理。现有 `DoseDecisionUseCase` 与 effects 接口保持唯一动作入口。重复点击返回 AlreadyDecided；effects 允许清理该 occurrence 的残留通知，但不能删别次 pending。

新增 pending DAO 和 repository 方法放在已有文件：查全部/按药查询、幂等写入及阶段升级、按 occurrence 删除、按 slot 清理。在 `MedicationReminderApplication.kt` 的 Room builder 注册 MIGRATION_3_4。

汇总从现有 repository 历史 Flow 派生，做窗口过滤及分组求和；记录规模仍受既定保留范围约束，不新增缓存、后台作业或统计持久化模块。

## 5. 文件级实施任务与依赖

### P1 — 持久化、间隔计算与决策身份

输入：本计划第3、4节，现有实体/DAO/Repository/Calculator及已有测试。

1. 修改 `Entities.kt / Daos.kt / MedicationDatabase.kt`，注册迁移，扩展 domain models 和 repository 映射。
2. 为 `NextDoseCalculator` 增加 interval next/recent-overdue 计算；next 返回严格晚于参考时刻的候选，alarm 首次恰好到点由调度层显式处理，不能无意跳过。
3. 修改保存与 slot 协调：固定模式保留原 reconcile；间隔模式仅一个 slot；改变序列参数换 slot，普通元数据保持原身份。
4. 修改 occurrence validation 与决策查询，保留固定时刻 logical-day 行为；将剂量快照和 pending 清理纳入同一事务。
5. 更新调用点使其编译并覆盖两种模式；此阶段不宣称 UI 已能创建间隔计划。

完成条件：迁移保留原药/时刻/历史；12小时、非24小时整除间隔跨日正确；同日多次决策独立；重复操作不增记录；旧固定时刻测试继续通过；新字段贯穿数据层。

### P2 — 提醒链路、倒计时与响铃开关

依赖：P1独立 review 通过。

主要文件：`ReminderScheduler.kt / ReminderReceiver.kt / ReminderDelivery.kt / ReminderNotifications.kt / ReminderDismissReceiver.kt / ReminderActionReceiver.kt / ReminderDoseDecisionEffects.kt / MissedReminderReposter.kt / SystemEventReceiver.kt`、`AppPreferences.kt`、`MedicationReminderApplication.kt`、`MedicationViewModel.kt`、`MedicationApp.kt`、`MedicationDetailScreen.kt`。

1. Scheduler 给同一 slot 注册 PRE/DUE，保留现有 exact-alarm 检查、SecurityException 处理及授权恢复，不新增降级调度策略。
2. Receiver 按 phase 分支，PRE仅推进下一PRE；DUE更新当前通知并仅推进下一DUE。重复和过时投递用现有合法性/决策检查抑制；当前已到DUE时迟到PRE不能降级状态。
3. 实现 pending 保存/恢复和完整通知身份。重建新alarm时取消旧 `REMIND.<slot>` alarm，保留旧action解析中的occurrence extras；旧alarm无phase仅解释为DUE。升级时不得盲目清除旧slot-id通知：继续显示并接受旧action；收到其content/action传入的完整occurrence后才可登记pending转新tag或完成决策，随后清理对应legacy通知。Android不能直接读取PendingIntent内部extras，不能假定可从旧通知准确恢复原服药时间。升级前未落库的旧通知无法承诺跨重启无损恢复；新版本新建pending全部按本计划持久化。
4. effects 只取消选中 occurrence，future rebuild 从 now 计算；处理旧通知不撤销别次通知。
5. 在现有编辑页加入模式/anchor/interval、结构化剂量控件；首页与详情按第3.4节统一 occurrence 选择和前台倒计时；保留当天待服回退、显式通知、keyed 状态、DST 及 onNewIntent/singleTop 修复，不另建状态框架。
6. 设置页加入响铃开关、两种 channel 发送策略；新增中英文资源，保留自启动手动引导。通知沿用现有 alias/通用标题、公开可见及剂量显示行为；基线没有 publicVersion，不宣称锁屏脱敏，也不新增该功能。
7. 保存路径按计划是否变化处理取消与重建，不能在纯剂量编辑时消失未处理通知。

完成条件：08:00对应07:55预提醒，08:00原通知到点更新；开应用仍操作08:00；同日20:00另一次独立；重复刷新不响；关闭响铃仍显示通知；Taken/Skipped只清理本次；恢复原 pending 不依赖补发开关。

### P3 — 剂量汇总页面

依赖：P2独立 review 通过（直接消费P1的字段及事件快照）。

主要文件：`Daos.kt / MedicationRepository.kt / MedicationModels.kt / MedicationViewModel.kt / MedicationApp.kt`，允许新增紧凑的 `ui/DoseSummaryScreen.kt`，以及现有英/中文 strings。

1. 历史映射优先使用事件剂量快照；旧事件明确未知，不把当前 dosageText 当历史证据。
2. 在同一历史 Flow 上实现第3.5节汇总；用 pure aggregation helper 方便针对实际算例测试。
3. 在 HistoryScreen 空/非空分支外加顶部入口，接入已有 NavHost 的 history/summary route；同一筛选窗口驱动次数、总量与未知提示。
4. 展示分药分单位结果、范围说明及空状态；不加图表、导出或历史编辑。
5. 集成验证五项需求，补充 feature 完成状态及发布说明。

完成条件：0.5+0.5=1准确显示，Skipped不计量；修改药物后旧量不变；未知量显示缺口；跨单位分开；按确认时间归属；空历史可进入和返回。

### P4 — 验证与发布准备

依赖：P3独立 review 通过。

运行第6节已有自动化及一次聚焦实机检查；仅修本 feature 的实际阻塞问题，修复后在独立 review 轮核实。更新 `docs/memory.md`、本计划状态和发布说明，增加 `app/build.gradle.kts` 的 versionCode（当前为4，新值须大于实施时基线已有值）并按现有版本惯例定 versionName，不将现有 `1.0.0-beta.4` 当作本 feature 的新版本。

交付可构建源代码、通过/未通过/未执行结果、APK路径（实际构建成功时）及签名方式。沿用现有 CI 和 release workflow，不创建新发布流水线。发布签名材料不可用时明确“debug验收包/正式签名包待本地构建”，不得将 debug APK 标作正式 release。

完成条件：五项需求的验收有证据、无 blocking finding、数据升级检查通过；构建与设备未执行项如实列出。合并、tag、GitHub release 仍由用户决定，技术可发布不等于自动发布。不得卸载现有应用来规避签名冲突而丢失数据；覆盖升级必须使用一致签名。

## 6. 必要测试与验收

扩展已有 JUnit/Robolectric 测试；不另建测试框架，不要求随机压力、全时区穷举、权限排列穷举或模拟与本功能无关的攻击。

| 验证点 | 用例及通过条件 | 复用位置 |
| --- | --- | --- |
| 迁移保留数据 | v3样例升级v4，药/固定时刻/旧事件保留，旧量null，新表可用 | MedicationDatabaseMigrationTest |
| 间隔递推 | 08:00+12h同日20:00再次日08:00；10h跨日、不重置到每日固定时刻；换区不改epoch | NextDoseCalculatorTest |
| 决策与数量 | 同日两次各可Taken；同一次双击/通知+详情只一行；固定模式原测试通过 | RoomMedicationRepositoryDoseDecisionTest、DoseDecisionUseCaseTest |
| PRE/DUE | PRE不替换DUE；1分钟间隔下一PRE在本次DUE前仍可触发；进入5分钟窗口重建可提醒；提前处理后两阶段均不响；迟到PRE不倒退DUE | ReminderSchedulerTest、ReminderDeliveryHandlingTest、DecisionAwareSchedulingTest |
| 持久身份与选择回退 | 未处理旧剂量与下一次同时存在；处理旧剂量只清自身；scheduleAll/relaunch恢复pending；点击通知保留原occurrence；多个持久pending选最早；无pending的固定模式仍选当天最近未决策到期项再未来，保留keyed状态和DST回归 | PersistedDecisionRescheduleTest、MissedReminderReposterIntegrationTest、HomeDetailOccurrenceTest / IntegrationTest、MedicationDetailLogicTest、现有navigation/entry测试 |
| 倒计时 | 进入5分钟窗口显示倒计时；到点显示到期；主页进入仍绑定未处理剂量；屏幕外无每秒任务 | MedicationDetailLogicTest + 一次UI检查 |
| 声音 | 默认开、持久化关闭、PRE/DUE各一次、重贴静音；切换不删除pending | AppPreferences/notification针对性测试 + 实机 |
| 汇总 | 小数求和；Skipped排除；不同药/单位分开；未知量提示；剂量编辑不改旧快照；按actionedAt窗口 | 在repository/domain测试目录添加一组汇总用例 |
| 编辑影响 | 普通元数据/剂量编辑保留pending；改序列取消旧通知与未来alarm且新序列生效 | DoseTimeReconciliationTest + reminder集成测试 |

按用户指定，后续完整验证由主助手在其自己的 VM 拉取对应 feature 提交后执行，或由用户日后在本地构建。用户后续允许先尝试环境中可行的验证；若构建/测试受阻，继续静态实施与审阅并明确列出未执行项，不在 Codex 云环境反复尝试相同故障。VM/本地的 JDK/SDK、依赖可用性与结果以实际执行为准。沿用仓库完整回归任务（仓库根目录，按实际环境配置 JDK/SDK 后执行）：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon
```

根 AGENTS.md 和 `docs/operations.md` 中的 Windows 路径及 wrapper 命令仅为历史工作站记录，不作为上述 VM 已可构建的证据。未安装依赖、无 SDK 或实际命令失败时报告具体原因；未运行、缓存复用和实际执行须分别说明，历史 135 项测试不替代当前验证。P1/P2/P3各运行可行的受影响测试及编译，受阻项交由主控VM/用户本地验证，P4运行一次完整现有回归；仅在新变更或失败时重跑相关项，不为纯文档变动运行构建或反复全量测试。

实机最小流程：在保留原数据的前提下建一个独立测试药，首次时间设置为数分钟后，使5分钟窗口能完整观察；验证PRE、DUE、声开关、点击进入仍绑定本次、Taken/Skipped后消失、下次不覆盖旧次；重开应用看pending恢复；检查记录页汇总和一个既有固定时刻提醒。可用短间隔测试药触发同日多次，不等待真实12小时；12小时数学结果由单测验证。新tag与尚未操作的legacy通知可能短暂并存，不能为消除过渡期重复而猜测旧occurrence并删通知；首次从旧通知进入或操作时完成精确收敛。实机声音、锁屏和ColorOS后台结果必须实测或标未执行，不能用源码断言替代。测试结束仅删除测试药，不清库、不卸载。

阻塞发布的是真实需求失败：漏/错次提醒、重复服药记录、旧数据丢失、汇总错量、关闭声音仍由应用主动响、编译失败。已知厂商自启动限制如实记录并沿用现有使用指导；风格偏好、无触发条件的假想风险不阻塞。

## 7. 后续执行与审阅规则

每次读取：根 AGENTS.md → docs/review/README.md 的 Latest review → docs/architecture/README.md → 本计划和当前阶段 → 当前代码/diff。只在本 feature branch 工作，不改 master、不force-push、不自行PR/merge/tag/release。

本计划涉及 Room v3→v4 迁移、持久 pending 与旧 alarm/通知兼容，开始 P1 前须完成独立计划审阅。主控已转达针对 `be77e1a` 的独立计划审阅结论为 PASS、无阻塞，审阅未修改代码；本轮据此进入 P1，并不把 implementer 自检当作独立审阅。该审阅的非阻塞提醒留在 P2：补充数据库操作失败反馈，失败时保留通知。现有 P1–P4 及阶段独立审阅规则保持不变。

沿用项目两项历史任务所规定的状态机，字母表示每个阶段的执行状态：

- A：当前阶段未完成，只开发当前阶段；完成后停止。
- B：阶段实现已完成但无对该实现有效的 review；下一轮只审阅，不同轮修复。
- C：review有未解决的阻塞问题；下一轮只修该阶段，完成后停止并等待独立review。
- D：review为 PASS 或 PASS WITH NON-BLOCKING FINDINGS，才进入下一阶段。非阻塞建议不伪装成必须修复。

review 使用新时间戳文件 `docs/review/YYYY-MM-DD-HHmm-sgt-<scope>.md`，同次更新 Latest review 指针；记录阶段、审阅实现commit、范围、真实触发条件/后果、blocking区分、测试证据及结论，不改旧review。不因每阶段审阅另加审批或重复验收层。若并行用子代理，只做阶段内互不冲突的调查/实施，主控串行整合共享文件；独立审阅仍单独一轮。

每轮结束记录分支/commit、阶段、A/B/C/D起始和结束状态、修改或审阅内容、实际测试与未执行项、是否可推进。不把计划本身的提交标为功能实现完成。

本次能查询到的当前定时任务列表未返回这两个项目任务；历史上下文找回了上述开发/审阅规则，但未取得可验证的当前任务ID与完整实时配置。本计划采用已确认规则，不声称已接管、修改或重启旧自动化。当前用户明确要求建立新 feature branch，本分支取代旧任务中的工作分支限制；其他不碰master、不自行发布规则继续保留。

## 8. 状态跟踪

| 阶段 | 当前状态 | 下一动作 |
| --- | --- | --- |
| 计划基线修订 | 已落实；主控转达独立审阅 PASS，无阻塞 | 保留该基线与审阅约束 |
| P1 数据与间隔 | B / 实现已交付，独立阶段审阅待进行 | 独立审阅本阶段；受阻的Room迁移/仓储验证如实延期记录，阶段推进依审阅结论 |
| P2 提醒与交互 | 未开始，依赖P1通过 | 等待P1有效review |
| P3 汇总 | 未开始，依赖P2通过 | 等待P2有效review |
| P4 验证与发布准备 | 未开始，依赖P3通过 | 集成验收与交付 |

### P1 交付检查（2026-11-03）

- 已实现：Room v4 迁移及旧迁移链、interval/dose-snapshot/pending 数据映射、稳定 slot 协调、固定锚点 interval next/recent-overdue、统一 occurrence 决策查询与校验、决策事务内快照及 pending 清理；相关调用点可编译。旧编辑器只保留尚无控件的新字段，不声称能创建间隔计划。
- 自检：主源码/KSP及测试源码编译成功；40项针对性 JVM 测试通过（calculator 12、Detail 17、delivery 4、slot reconcile 7），debug APK 实际重新打包成功。迁移的10条实际 SQL 在 SQLite 执行后，5张表的列/默认值/外键/索引与 KSP v4 schema 一致，旧数据/null快照/复合唯一性及级联检查通过。此 SQLite 检查不等于 Android Room 运行验证。
- 限制：首次完整 `testDebugUnitTest assembleDebug` 在9个 Robolectric类初始化时因 `/home/agent/.robolectric-download-lock` 不可写而失败（101项中92通过、9项初始化失败）；这些类的方法未进入执行，包括新增 Room 迁移和 interval 仓储集成用例。不以旧报告或后续40项针对性通过掩盖该失败；完整套件、Room运行验证、lint及实机验收仍待主控VM或用户本地执行。未反复重跑相同的受阻初始化。按用户后续指示，环境受阻检查可以延期，不因此停止其他可做的静态实施和独立审阅；审阅必须保留这些未验证项，不得写作通过。
- 交付状态为 P1 / B，尚未获得独立阶段审阅或整体验收；PRE/DUE投递、pending恢复/首页选择、响铃设置、汇总页面均留在后续阶段。

## 9. 核对依据

仓库事实取自本计划所列基线代码，以及：

- `AGENTS.md`、`docs/review/README.md`、最新 `docs/review/2026-10-24-2333-sgt-reconstructed-autostart-pending-dose.md`；2026-09-21 设备报告仅作历史系统限制依据。
- `docs/architecture/README.md`、`docs/feature-plans/medication-detail-dose-actions/README.md`、`docs/operations.md`。
- `ui/HomeDetailOccurrence.kt`、`MedicationDetailScreen.kt`、`MedicationViewModel.kt`、`MedicationApp.kt`、`AutostartSettings.kt`、`MainActivity.kt`、`AndroidManifest.xml` 和 `reminders/ReminderNotifications.kt`。
- `.github/workflows/ci.yml`、`.github/workflows/release.yml`、`app/build.gradle.kts`。

Android官方接口依据（原计划记载于2026-11-03查询，本轮仅保留引用，未重新联网核验）：

- [通知channel及用户声音设置](https://developer.android.com/develop/ui/views/notifications/channels)：已创建channel的声音行为不能由应用随意重设。
- [Notification.Builder](https://developer.android.com/reference/android/app/Notification.Builder)：chronometer与倒计时接口。
- [Android 14通知行为变化](https://developer.android.com/about/versions/14/behavior-changes-all)：ongoing通知不等于所有系统情形下不可清除。
