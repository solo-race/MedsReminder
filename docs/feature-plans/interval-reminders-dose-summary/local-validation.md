# 本地最小验证 — beta.5 / code 5（尚未发布）

云环境P4已通过175项完整测试、lintDebug（0错误）和assembleDebug；下列步骤供本地复现及补齐设备验收，不代表已经执行本地测试。详细结果、警告、签名及APK指纹见 [`../../memory.md`](../../memory.md)。

## 构建与自动化

使用feature分支 `codex/feature-interval-reminders-dose-summary` 的交付提交。准备JDK17、Android SDK platform `android-37.0`、build-tools `36.0.0` / `37.0.0`（与现有CI一致），并使用可写的Gradle缓存。依赖未缓存时，需要正常访问Gradle、Google及Maven仓库，不加 `--offline`。从仓库根目录执行；Windows PowerShell示例：

```powershell
$env:JAVA_HOME = '<本机JDK17目录>'
$env:ANDROID_HOME = '<本机Android SDK目录>'
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"
& "$env:JAVA_HOME\bin\java.exe" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) { throw '验证失败，请保留完整日志' }
```

Linux/macOS在配置同样的JDK/SDK后使用 `./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --console=plain`。结果为 `app/build/test-results/testDebugUnitTest/`、`app/build/reports/lint-results-debug.html` 和 `app/build/outputs/apk/debug/app-debug.apk`。

只有遇到只读home且已有匹配的Robolectric SDK jar时，才追加 `-ProbolectricDependencyDir=<包含SDK-jar的目录>`；本分支将其转为官方 `robolectric.dependency.dir`，直接使用本地依赖，缺文件会失败，不回退下载。[Robolectric本地依赖配置](https://robolectric.org/configuring/)、[解析行为说明](https://robolectric.org/blog/2023/11/11/improving-android-all-downloading/)。本轮API35使用 `android-all-instrumented-15-robolectric-13954326-i7.jar`；普通可写home无需该参数。Kotlin daemon无法写marker时可用官方 `-Pkotlin.compiler.execution.strategy=in-process`，不需要更改home或文件权限。[Kotlin编译策略](https://kotlinlang.org/docs/compiler-execution-strategy.html)。全部依赖已缓存才可追加 `--offline`。

## 设备与数据验收（本轮未执行）

1. 连接测试设备，用 `adb devices -l` 确认可用。覆盖现有应用须使用与已安装应用一致的签名，先记录版本及原有药物/历史；`adb install -r app/build/outputs/apk/debug/app-debug.apk` 的完整结果必须成功，再核对 `adb shell dumpsys package com.example.medicationreminder` 显示beta.5/code 5。若提示 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，停止覆盖，改用匹配签名包或独立测试设备；不要卸载或清数据。此处保留数据要求优先于旧Windows操作记录的卸载示例。
2. 仅新建独立测试药，首次时间设为6分钟后、间隔设为几分钟、剂量0.5片。观察前5分钟PRE、到点DUE、倒计时及声音各一次；关响铃后下一次仍显示但不由应用响铃；重贴/重开不重复响。允许系统通知及精确闹钟，记录系统channel/勿扰/OEM自启动配置。
3. 保留一次未处理提醒直到下一次出现，确认两次独立；首页/通知进入仍指向对应次。分别Taken/Skipped只清理所选次，重复操作不增加记录；普通备注/剂量编辑保留pending，变更计划后旧序列结束。关闭“补发错过提醒”后重开仍恢复已有pending；不要用强制停止作为普通进程重启。
4. 验证A详情→A编辑→点击B通知→连续返回A详情，再Taken/Skipped能成功并推进。另检查旧决策未完成时点击新通知不会被旧回调覆盖。检查旋转、后台后回前台及跨到点状态；PRE已发、DUE尚未处理时在到点清除通知，真正DUE应仍保留响铃机会。
5. 空记录能进入汇总并返回；两次Taken的0.5片合计1片，Skipped不计量，不同药/单位分别列出；修改当前剂量不改变历史快照，旧无快照记录显示缺口。切换今天/7天/30天并核对确认时间、午夜边界，以及既有固定时刻提醒。仅删除独立测试药结束，不清库或删除原用药。
6. 使用匹配签名的旧版本及新版本在测试设备覆盖升级，核对旧药/时刻/历史保留及新旧通知过渡；未落库的旧通知不承诺跨重启恢复。实际检查锁屏、重启后pending恢复及ColorOS允许自启动的后台投递；记录未执行/系统限制，不用Robolectric通过代替设备结果。

debug包不用于承诺正式签名覆盖升级。本环境没有正式签名材料，`assembleRelease` 未执行；需要正式验收包时沿用仓库现有签名配置在本地运行 `assembleRelease`，保留原签名。合并、tag和发布均未执行。
