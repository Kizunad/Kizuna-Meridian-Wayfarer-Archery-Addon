# 验证

```sh
./gradlew --no-daemon test build :gametest:runGametest --console=plain
python3 "scripts/check_docs.py"
python3 "scripts/check_data.py"
git diff --check
```

共享机器同一时刻只运行一个 Gradle；使用 `/tmp/kizuna-gradle.lock` 阻塞锁串行构建，
拿到锁后仍检查全机 Gradle 进程，确认没有未遵循此锁的其他构建。
GameTest 每局使用新的运行目录，完整装配缺少任何必装产物都明确失败。

## 验证边界

- 数据检查：无入口元数据、硬依赖、唯一 txtpb、两种弓梢伤害和追加标签；26 张专用美术资源的名称、PNG 头与尺寸。
- 目录回归：真实 Human 模板注册、一书三卷十式、总做功与箭数守恒、相对拉弦臂槽、
  拉空弓与天地同至约束、正式 JAR 的唯一目录和全部资源逐字节一致且无 Java 类。
- 本仓整合 GameTest：发行包自行发现目录，真实 Human 玩家宿主学习并施放满月、副手连弩、拉空弓；
  检查做功早于放箭、五段费用与五发实际箭、弹药消耗、收势和断线后未开始段不扣；
  拉空弓验证六肌逐段训练负荷、收势后的永久耐力成长，以及断线保留已付训练负荷。
- 箭属性、弩原装填、附魔、掩体后箭雨与锁点由 Human 整体回归覆盖；
  生物选式、换兵、手位和原版互斥由 Threatening 整体回归覆盖。各仓记录区分实际通过与未执行项目。
- 客户端动画观感、弓弩物品模型、专服多人和数值平衡需单独验收。
- 天地同至的到时登记、登记前取消、登记后完整运行、迟到和事务重试由 Technique 回归覆盖；
  待登记曲线参与未来平射预测但不改写身体，由 Human 真实箭回归覆盖。

## 当前验证记录

2026-11-03 补齐正式 txtpb、Framework assembly 条目、5 条目录回归和元数据已声明的
`WayfarerGameTest` 入口。运行 `bash "gradlew" --no-daemon test build :gametest:runGametest`，
独立目录 `/tmp/wayfarer-review-20261103/game-wayfarer-1`：5 项 JUnit、4 项必需 GameTest
（3 项本仓测试和 Fabric 基础用例）通过，退出码 0。
日志 `/tmp/wayfarer-review-20261103/wayfarer-test-build-gametest.log`。

`check_docs.py`、`check_data.py` 通过；26 张 PNG 与任务开始时 SHA-256 一致，未改动美术。
正式 JAR 无 Java 类，目录和资源与源码逐字节相同；验证 Java 仅属于验证模块。
此前骨架构建因缺 txtpb 失败、GameTest 为 `NO-SOURCE` 的历史记录保留在交接报告，
不能与本次真实测试结果混计。做功与视觉区间的占位差异见[目录与时序](Catalog.md#做功)。

### 2026-11-03 训练与曲线收尾

拉空弓五段训练已启用，天地同至声明到时登记曲线。正式目录的 5 项 JUnit、
5 项必需 GameTest（4 项本仓测试和 Fabric 基础用例）全部通过；本轮真实验证包含
每肌五段共 1350 J 训练负荷、收势后耐力成长、断线只保留首段 270 J、无箭和不扣弓耐久。
日志 `/tmp/wayfarer-final-20261103-kzzaw3ub/wayfarer-full.log`，独立运行目录为同级 `game-wayfarer/`。

Technique 新增 10 项到时曲线回归，完整单测与 GameTest 通过；Human 新增真实箭预测回归，
完整 91 项 GameTest 通过。预测把同次施放的待登记曲线加入只读候选，当前身体和当前拉弦系数保持原值。
登记之前终止不会产生曲线；登记后仍按原绝对时刻完整运行，迟到不会延长增益或虚脱。

### 2026-11-03 主会话接管复核

重新审读目录、分段费用、未来曲线、持物约束、换兵、弹道和表现接线。
本仓目录无需重写；掠夺者武器分类与持物预览改为复用 Human/Technique 的公开入口，
Human 弹道重复积分改为等价离散求和，并用独立逐 tick 递推逐项比较整个搜索区间。

本轮 5 项目录 JUnit、5 项 GameTest 均通过；Technique 245 项、Human 75 项 JUnit 通过，
Human 完整 91 项和 Threatening 完整 90 项真实 GameTest 通过。
后者包含掠夺者自然弓弩施放、旧配置和交回原版射击 Goal。

PA 当前工作目录已包含十式主副手二十段资源，实际弓弩属性读取动画时钟。
19 项真实客户端测试通过，覆盖左右惯用手玩家和掠夺者、主副手隔离、烟花显示类型和原版回退。
Mobs 客户端回归完成 101 个固定场景和一个自然施放场景，其中行矢 60 张截图已逐张查看。
固定截图检查剪辑姿态与持物外观，不替代连续播放观感和战斗平衡验收。

命令、退出码、日志和相对接管基线的 diff 保存于远程会话目录
`handoff/gear-wayfarer-owner-review/`。Windows 试玩实例尚未更新，本轮未做远程多人验证。
