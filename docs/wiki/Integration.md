# 接口与装配

正式包硬依赖 `meridian_technique`、`meridian_vanilla_damage`、`meridian_human_host`。
模组没有入口或执行类；`custom.meridian_technique:catalogs` 声明唯一 textproto。
与当前千里腿和破门斩保持一致，包使用 `environment: "*"`：两端需发现同一清单并注册卷轴。
这是相对原设计“只装服务端”的装配调整，身体和射击结算仍只在服务端执行。

三个卷轴为 `meridian_wayfarer:bow_upper_scroll`、`crossbow_scroll`、`bow_lower_scroll`。
已装入三卷专用卷轴图标和阅读背景，由功法库根据卷 ID 加载；不创建替代 UI。

## 美术资源与目录身份

审阅方提供的 26 张 PNG 已原样复制到 `fabric/src/main/resources/assets/meridian_wayfarer/textures/`。
原稿与提示词保留在交接目录 `art-wayfarer`，不进入本仓。

战斗图标的 `presentation.icon_id` 固定为 `meridian_wayfarer:<式名>`，不带路径、`.png` 或 `_64`。
式名为 `full_draw`、`pearls`、`bow_strike`、`dry_draw`、`repeater`、`gale`、`kneeling`、
`parthian`、`arrow_rain`、`convergence`；战斗 UI 将其映射到 `gui/technique/<式名>.png`。
每式同时保留 32 × 32 原文件及同名 `_64.png` 的 64 × 64 版本。

| 卷 ID | 卷轴物品 ID | 卷轴贴图（32 × 32） | 阅读背景（1536 × 1024） |
|---|---|---|---|
| `meridian_wayfarer:bow_upper` | `meridian_wayfarer:bow_upper_scroll` | `item/bow_upper_scroll.png` | `gui/scroll/bow_upper.png` |
| `meridian_wayfarer:crossbow` | `meridian_wayfarer:crossbow_scroll` | `item/crossbow_scroll.png` | `gui/scroll/crossbow.png` |
| `meridian_wayfarer:bow_lower` | `meridian_wayfarer:bow_lower_scroll` | `item/bow_lower_scroll.png` | `gui/scroll/bow_lower.png` |

功法库现有客户端加载器按卷身份生成卷轴物品定义与 generated 模型，本包无需重复提供模型 JSON。
正式 txtpb 使用以上图标和卷身份；动画表现 ID 单独保存于时间线的 `presentation` 效果。

## 持物与伤害

- `#meridian_wayfarer:bows` 默认只有弓，`crossbows` 默认只有弩。
- `ammo` 默认包含普通箭、药箭、光灵箭；`crossbow_ammo` 引用该标签并加入烟花。
  目录的弓类别使用 `ammo`，弩类别通过 `HeldItemCategory.ammunition_tags` 覆盖为 `crossbow_ammo`。
  弩的烟花只从另一只手取，不能从背包取，也不能供弓使用。
- 原版顺序为副手、主手、背包；创造、无限弓普通箭与生物不耗弹药的事实由宿主返回。
- 弩中原本 `CHARGED_PROJECTILES` 不参与功法装填或消耗。每发使用宿主当前弹药和附魔。
- 弓梢使用 `bow_strike_main`、`bow_strike_off` 两种伤害类型，均追加到 `meridian:melee`、
  `meridian:kind/blunt`；仅副手追加到 `meridian:off_hand`，不声明双手或腿部标签。
- 弓梢是单体 3 格、固定钝击 2.5、击退 1.2，握弓臂按 Damage §5 出力，打击另耗 1 耐久。
- 原版箭未追加到 `melee`；拉弦系数由 Damage 只读接口乘在初速上，命中时不重复乘。

Damage 的 `VanillaDamageFabric.armCoefficient(entity, HumanoidArm)` 读取当前权威身体，
调用方传入拉弦臂的解剖侧。没有身体取 1，伤势、疲劳、能力曲线与近战使用相同算法；查询不记做功。
天地同至预计到达时刻使用 `armCoefficientAt(entity, arm, referenceMillis)`，只在内存候选中评估
已登记的未来曲线，保持当前伤势与疲劳，不推进权威身体；实际撒放重新查询当前系数。

## 射击宿主

`launch_projectile` 意图包含起手选定手、冻结的持物规则、原剪辑起播偏移和施放开始时刻。
`target_lock` 只在起手发一次，宿主按施放 ID 保存锁定目标、位置和速度，重试保持幂等。
目录只声明倍率、方向、散布、穿透、拉弦臂和盾冷却，箭／烟花实体、弹药、耐久、附魔、声音归宿主。

箭满弓原速 3.0 格/tick，弩箭 3.15 格/tick；弹道求解按每 tick 重力 0.05、空气阻力 0.99。
这些实现须以 Minecraft 26.2 的宿主源码验证；本包不复制原版算法。
力量、冲击、火矢、无限、多重射击、穿透与耐久附魔均交宿主按原版接口处理。
烟花爆炸不吃本式箭伤倍率。弓弩外观、物品模型状态和无箭弓模型不属于本包。

## 本地工作区

Java 25、Gradle 9.8.0、Loom 1.19.3、Minecraft 26.2、Fabric API 0.161.0+26.2。
`settings.gradle` 通过 `kizunaWorkspace.includeSource` 引用当前 Framework、Technique、Human、Damage，
没有复制协议或伪造已发布 Maven 坐标。版本和仓位置由 Framework 的工作区脚本提供。

Framework 的 `assembly.json` 以 `wayfarer-archery` 登记本仓，`fabric` 为
`candidate: data-mod`、`runtime: mod`。
`full-assembly.gradle` 遍历同一清单，自动装入发行包；完整装配保留 BS3D、PA 等必需依赖。
验证代码只在 `verification/` 和 `gametest/`，不会进入正式 JAR。
