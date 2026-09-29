# AGENTS.md — Crafty Cards · neoforge / Minecraft 1.21.3

本分支固定为 neoforge / Minecraft 1.21.3，Java 21。运行 `powershell -ExecutionPolicy Bypass -File .\build-target.ps1`，安装用 JAR 为 `build/mc-1.21.3/libs/crafty_cards-neoforge-mc1.21.3-1.0.0.jar`。下文中关于其他加载器的内容仅供移植背景参考，不是本分支的构建目标。

## 构建命令

```powershell
powershell -ExecutionPolicy Bypass -File .\build-target.ps1
```

## 架构

- **Mod ID**: `crafty_cards`
- **入口**: `CraftyCards.java`（`@Mod(CCReference.MOD_ID)`）
- **包名**: `com.jokernan.craftycards`
- **注册方式**: NeoForge `DeferredRegister` 模式 — `DeferredRegister.Items` / `DeferredRegister.Blocks` / `DeferredHolder`
- **卡牌翻面**: `ItemCardCovered.use()` 右键翻牌（无需网络包，单机/联机一致）

## 从 PlayingCards (Forge 1.20.1) 移植

关键 API 变更：
- `@Mod` 构造函数注入 `(IEventBus, ModContainer)`
- `defineSynchedData(SynchedEntityData.Builder builder)` (builder 模式)
- `EntityStacked.moreData()` 改为 `moreData(SynchedEntityData.Builder)`
- `ItemHelper.getNBT()` 使用 `DataComponents.CUSTOM_DATA`（1.21 的新组件系统）
- 实体不再需要 `getAddEntityPacket()` / `NetworkHooks.getEntitySpawningPacket()`
- `BlockBarStool.use()` → `useWithoutItem()` (1.21 变更)
- `TileEntityBase.load()` → `loadAdditional(CompoundTag, HolderLookup.Provider)`
- 配方 `matches()` / `assemble()` 使用 `CraftingInput` 而非 `CraftingContainer`

## 斗地主引擎

- `game/DDZEngine.java` — 纯 Java 逻辑，不依赖 MC 环境
- 54 张牌（0-53），52=小王，53=大王
- 3 人局，每人 17 张 + 3 张底牌
- 牌型：单张、对子、三条、三带一/二、顺子、连对、飞机、炸弹、火箭

**"四张同点"是牌型判定里最容易出错的一处**：`analyzeCardType` 把"≥3 张的点数"收进
`triples` 供飞机判定，于是一个四张的点数会**同时**是"炸弹"和"三条候选"，
而这个点数的张数（4）与"三条"（3）并不相等——凡是按"点数个数"而不是"牌张数"做的校验，
都会被这个差额骗过。踩过的实例：飞机带对的带牌要求"两个对子"，原先只比点数个数，
`3333 444 55 6` 里 `55 + 6` 只有 1 个对子却凑够了"2 个点数"，被判成飞机带对（10 张一次甩掉）。
现在带牌要求**每个点恰好 2 张**。加飞机相关规则时，请一律用牌张数做校验。

## 斗地主联机（3 人）

服务端权威引擎 + 全量状态快照同步，只做联机（无本地单人入口）。

- **入口**：`block/BlockDDZTable.java` 牌桌方块（**单方块 1×1**）放进世界，玩家右键加入，3 人坐满自动开局
- **服务端**：`game/server/DDZTableManager.java`（单例，`Map<TableKey, DDZSession>`）+ `game/server/DDZSession.java`（3 座位 + 引擎）
  - 所有牌局逻辑在服务端 `DDZEngine` 执行，服务端权威校验回合/手牌/牌型
  - 掉线（`PlayerLoggedOutEvent`）清位或解散；拆桌（`BlockEvent.BreakEvent`）解散
- **网络**：NeoForge payload（`RegisterPayloadHandlersEvent`，`network/DDZNetworking.java`）
  - C2S：`PlayerActionPayload`（JOIN/BID/PLAY/PASS/LEAVE/WATCH/OPEN_TABLE_CONFIG/SAVE_TABLE_CONFIG/CLOSE_TABLE_CONFIG，枚举顺序即编码顺序、只能往末尾追加；`TableKey`=维度+坐标）
  - S2C：`GameStatePayload` 29 字段全量快照，NBT 序列化（`toTag`/`fromTag`），**按接收者单独构建**（`myIndex` + 本人 `myHand` + `iAmHost`/`configOpen`），不共用同一 payload 广播
- **客户端**：`client/ClientDDZData.java` 缓存快照，驱动 HUD 叠加层 `client/DDZGameHud.java`（操作一律发 C2S 包，收到快照后刷新）；不再有全屏 Screen
- **世界持牌**：`client/WorldHandCards.java` 在 `AFTER_TRANSLUCENT_BLOCKS` 阶段渲染**各参局玩家身前**的一排立牌，跟随该玩家位置与视线（`forward×handForwardDist`、胸口高度）。牌面朝向持牌人：他看自己的牌面，对面看牌背；旁观者站到他身后能看到牌面。自己不渲染（HUD 已有）
  - **扇形前移量必须消掉 θ 项**：牌组横向等距排开、每张绕自身竖直中心转 θ 时，若前移量是
    "等量且恒定沿组坐标 +z"，它在每张牌法线上的投影是 `gap·sinθ + step·cosθ`左半边
    两项相抵（0.0074，比牌模型厚度 0.0125 还小 <A> 实体互相穿插）、右半边两项相加（0.0881），
    观感就是"同样的横向间隔，右侧却隔得远、和左侧不一致"。改为按**平面距离恒定**反解
    （`DDZGeom.fanDepthAdvance`）后左右一致；`handStackStep` 的含义随之变成"相邻两片的平面距离"，
    原先的 `handStackStepSafety` 自动补偿已删除。
- **牌面可见性（防作弊）**：服务端按接收者逐份构建 `GameStatePayload.visibleHands`（规则见 `game/DDZVisibility`，纯函数有单测）。客户端只拿得到服务端下发的牌面，拿不到就只渲染牌背——**可见性完全由服务端决定，不依赖客户端自觉**
  - 同一条道理适用于**底牌**：`getDipai()` 只在出牌阶段进快照。叫分阶段下发过它（当时只有
    HUD 的显示条件挡着，而"画不画"挡不住改过的客户端）——3 张底牌正是决定叫几分的依据

### 交互方式（HUD 模式）

HUD 不是全屏 Screen，而是叠加在主世界上的信息层，玩家能看到牌桌周围的世界：

- **筹码（按桌配置，房主说了算）**：本桌用哪种物品下注、每人押几个、门槛多少、这张桌还玩不玩筹码，
  都存在**牌桌方块实体**里（`blockentity/DDZTableBlockEntity.java`），由房主在配置界面里改（见下）。
  `server.json` 只管**总闸门** `chipsRequired`（关掉则全世界的桌子都不玩筹码），以及
  `chipItems`（白名单）与 `chipStake`/`chipEntryCount`（**新桌子的种子默认值**，桌子第一次被用到时种进去）。
  没配筹码的桌子**不允许入局**（服务端提示"本桌筹码还没配"，让玩家去叫房主）
- **加入**：未入局时右键牌桌方块 → 发 `JOIN` 包。服务端校验：本桌筹码已配、且在桌旁（`joinRadius`）、且背包（含副手/护甲槽）里筹码数量 ≥ 本桌门槛；满足后**收「本桌底注」个进物品池**（逐个 `ItemStack.split(1)`，附魔等组件原样保留）。**不绑定座椅实体**，入局后可自由走动
- **叫分**：叫地主阶段轮到自己时，滚轮在【不叫/1分/2分/3分】循环（`InputEvent.MouseScrollingEvent`，当前选项金色高亮），右键牌桌确认发 `BID`
- **选牌**：出牌阶段滚轮移动手牌焦点（黄色边框），**方向与滚轮相反**（向上滚左移、向下滚右移）；左键点击确认把焦点牌加入/移出选中集合（`handleMouseClick` 拦截并取消事件，防止误破坏方块）。**非回合也能预选**（滚轮不切物品栏）
- **取消选择**：出牌阶段右键**空气**（准星不指牌桌）→ 清除全部选中（`clearSelection`）
- **出牌/过牌**：右键牌桌方块 —— 有选中牌=出牌（本地 `analyzeCardType` 预检牌型），无选中牌=过牌（走方块 `useWithoutItem`）；新一轮牌权（上家是自己/无人出牌）不能过
- **结算**：SETTLED 阶段点击任意处关闭（`ClientDDZData.reset()`）；服务端已清退玩家但快照保留，结算界面继续显示三家剩余手牌
- **每轮倒计时（HUD）**：限时开着时，阶段那一行后面接一段" | 剩余 Ns"（`client/TurnCountdown`，
  纯函数有单测）：最后 10 秒转橙、5 秒转红，走完显示 0s（服务端最多再等一个清扫节拍就判负）。
  服务端只在快照里下发**剩余刻数**（`turnRemainingTicks`，`-1` = 不限时），客户端拿它当基准
  本地按 50ms/刻 续算（`ClientDDZData#turnRemainingTicks`），下一份快照到达时对齐——
  倒计时是每帧都在走的，不能靠服务端每刻推送。限时关闭（0）时下发 -1，HUD 什么都不画。
- **离开**：**Shift + 右键牌桌** → 发 `LEAVE`。自由走动，没有座椅、也没有下马事件
  - Shift+右键按身份分流：**参局者 = 离开**；**未入局 = 本桌配置界面**（`OPEN_TABLE_CONFIG`）。
    分流判定在**服务端**：本桌还没房主（或房主离线）→ 我成为房主并开配置界面；
    已有在线房主且不是我 → 回一句提示并**退回观战开关**（观战只有这一个入口，不能被配置界面挤掉）
- **房主与配置界面**：房主 = 局外第一个 Shift+右键这张桌的玩家，记在方块实体里（随桌子持久化；
  房主离线后下一位可以接管，否则房主一走这张桌就没人配得了）。界面 4 项：筹码类型 / 底注 /
  入局门槛 / 本桌启用筹码（`client/TableConfigScreen.java`）。**互斥**靠方块实体上的配置锁 +
  120 秒超时 + "持有者走远/掉线即释放"；锁在谁手上由服务端说了算，快照的 `configOpen`（按接收者算）
  就是"界面该不该开着"，锁被收回时服务端补一份 `configOpen = false` 的快照让客户端自己关掉界面
- 旁观：**必须主动开启**——非参局玩家靠近牌桌后 **Shift+右键**（不是房主时会走到这条路上）发 `WATCH`，服务端给只读快照（`myIndex = -1`），能看牌不能操作；再按一次停止。走近**不会**自动共享手牌（`server.json` 的 `spectateRadius` 默认 0，这是刻意的安全默认值：否则任何人在桌边都能看到三家手牌）

### 坑（重要）

- 双端都会加载的类（方块/物品/Manager 等）**方法体不得直接引用客户端类**：`getfield`/`instanceof`/`new` 会在类链接/验证时解析目标类，服务端会因加载 `@OnlyIn(CLIENT)` 的类（如 `Screen`）崩溃（"Attempted to load class ... for invalid dist DEDICATED_SERVER"）。客户端逻辑应收进只在客户端加载的类（如 `ClientDDZData`），双端类只保留纯方法调用
- payload handler 内必须 `context.enqueueWork()` 回主线程再操作 manager/engine
- 广播用 `PacketDistributor.sendToPlayer` 逐个发，不要用 `sendToPlayersInLevel`（会发给全服）

## 配置

| 文件 | 归属 | 内容 |
|------|------|------|
| `config/crafty_cards/visual.json` | 客户端 | 渲染参数（HUD 手牌/世界持牌/桌面出牌/筹码高度），`/craftycards reload` 热更新 |
| `config/crafty_cards/sounds.json` | 客户端 | 音频：总开关、共用音量、选用哪个音乐包（+ BGM 声道、是否压低原版音乐，这两项界面上没有入口，只能手改） |
| `config/crafty_cards/soundpacks/<包id>/` | 客户端 | 音乐包（`pack.json` + `*.ogg`），只由制作程序生成 |
| `config/crafty_cards/server.json` | 服务端 | 玩法开关：`spectatorsSeeCards`（观战者能否看牌面，默认开）、`participantsSeeFaces`（参与者互看，默认关防作弊）、`spectateRadius`（走近自动共享半径，**默认 0 = 关**）、`idleTimeoutTicks`（空闲销毁，默认 10 分钟）、`turnTimeoutTicks`（每轮限时，默认 30 秒，**0 = 关**；界面是开关 + 秒数档位，超时按判负，HUD 显示倒计时）、`joinRadius`（入局/观战距离，默认 8 格）、`chipsRequired`（**筹码总闸门**，默认开；关掉则全世界的桌子都不玩筹码）、`chipItems`（可作筹码的物品白名单）、`chipStake`（**新桌子的**入场底注默认值，默认 1）、`chipEntryCount`（**新桌子的**门槛默认值，0 = 自动） |

改 `server.json` 后要**重启**生效（它只在启动时读一次；`visual.json` 那类客户端渲染参数才有
`/craftycards reload` 热更新）。写入路径有一个踩过的坑：`save()` 必须序列化**当前值**
（`snapshot()`）——早先写的是 `new Data()`，于是落盘的是 `Data` 字段的**字面量默认值**，
表现为"手改配置一重启就变回默认"、界面上点「保存并生效」也永远写不进去。
`Data` 里的字面量只作**反序列化缺省值**（JSON 里缺某个键时生效，所以老配置文件缺
`chipsRequired` 也按"需要筹码"处理）。

**游戏内配置界面是"主页 → 分类页"的多层级结构**（`client/ConfigHomeScreen` 起）：

```
设置主页  ─┬─ 音乐包       SoundConfigScreen             总开关 / 音量 / 选包 / 扫描 / 包列表
           ├─ 玩法设置     ServerPlayConfigScreen        总闸门、新桌默认底注/门槛、距离、超时、看牌权限
           └─ 渲染参数     RenderConfigScreen            常用渲染项（其余在 visual.json）

（不在这个树里）本桌筹码配置  TableConfigScreen          筹码类型 / 底注 / 门槛 / 本桌启用筹码
                            —— 由服务端快照的 configOpen 打开，入口是"在牌桌前 Shift+右键"
```

**音频只剩「音乐包」一项入口**（这是刻意的）：音频的唯一接入接口就是音乐包，游戏内不再提供
"逐键指派文件 / 逐条调权重 / 试听 / 增删自备文件"这类直接改音频的操作——那三页（音效与语音、
牌型语音、背景音乐）与音频键详情页（`SoundKeyScreen`）已整体删除。要改音频就用制作程序改包。

四条约定（都是"改版前的界面很乱"的直接教训）：

- **排版算法抽成纯函数** `client/ConfigLayout` + 单测：改版前底部一行塞 7 个按固定 x 摆放的
  控件，小窗口下按钮叠在一起；列表高度是手填魔数，最后一行会被状态文字压住。
  现在列表高度、行内控件右对齐、文字可用宽度、底部按钮居中全部由 `ConfigLayout` 算，
  `ConfigLayoutTest` 在各种分辨率下断言"不重叠、不越界"。界面代码只消费这些函数，不再自己算坐标。
- **行 = 标签 + 提示 + 右侧控件**（`ConfigScreenBase.ConfigRow`）：不同页面用同一套行，
  所以看起来一致。加设置项就是 `list.row(choiceRow(...))` 一行。
- **提示文字不要写颜色码**：文字按宽度截断用的是 `plainSubstrByWidth`，它会把 `§` 码一起剥掉、
  颜色静默失效。要彩色就覆盖 `hintColor()`。
- **音频的编辑会话**（`client/SoundEditor`）：设置主页与音乐包页写同一个 `sounds.json`，
  各存一份工作副本的话，"在音乐包页选了包没保存、回主页"就会把改动吞掉。
  会话从主页进入时开始，关闭整个设置界面时若 `dirty()` 则先弹 `UnsavedAudioScreen`
  （保存并应用 / 放弃改动 / 继续编辑），绝不静默丢弃。
  （保存是重活：要重建资源包并重载音频，所以不做"改一下存一下"的写穿。）
  会话内容只有三项：总开关、音量、选哪个包——音频内容全在包里，不由界面编辑。

## 筹码与结算

- **可以整局不玩筹码（两种关法）**：`server.json` 的 `chipsRequired = false` 是**总闸门**，
  关掉则全世界的桌子一起变成纯娱乐局；每张桌子还能用自己那项「本桌启用筹码」单独关
  （关的是这一桌，别的桌照常赌）。两种关法下都是：不必配筹码、不审核背包、入局不收押注、
  结算不转移任何物品，适合创造模式/建筑服/活动用的临时牌桌。实现上靠会话的
  `requiredChips()` **返回 0** 这一个信号贯穿：入局校验与 `collectBet` 跳过、
  快照照发 `chipRequired = 0`、客户端据此把界面切成"不需要筹码"。
  **"这桌玩不玩筹码"一律看 `chipRequired`，不看快照里的 `betItem` 是否为空**：
  `betItem` 始终是"桌上配的那个物品"（关掉筹码也照发，配置界面要拿它回填——
  否则房主关掉再打开界面会看到"未选择"，顺手一保存就把自己选过的类型抹了）；
  桌面悬浮筹码与结算界面那一行都按 `chipRequired > 0` 判断该不该画。
  闸门关掉后再在配置界面里选筹码也**不会**开始收押注（闸门与桌开关是"与"）。
  （用 0 而不是另加一个布尔字段：快照本来就要下发"入局需要几个"，0 天然是"不玩赌注"，
  与 `myIndex = -1` 表示旁观者同一套做法；客户端也**不能**读自己那份 `server.json` 判断——
  连专用服务器时它与服务端的配置无关。）
- **筹码类型由房主在配置界面里选**（不是从背包里推断，也不是玩家"声明"）：
  候选是 `server.json` 的 `chipItems` 白名单（支持模组物品），服务端保存时**再校验一次**
  ——界面上的候选读的是客户端自己那份 `server.json`，连专用服务器时可能与服务端不同。
  筹码类型按桌存，改桌子就改这一桌，不牵动别人。
- **底注与门槛**：`ServerGameConfig.chipStake`（默认 1）与 `chipEntryCount`（默认 0 = 自动）
  现在是**新桌子的种子默认值**：桌子第一次被用到时把它们种进方块实体，之后只认自己的值
  （管理员改全局默认不会动到已经配过的桌子——这正是"每桌独立"要的）。
  本桌底注 = 每人押进池子的数量。本桌门槛（背包里要备够几个才准入）默认**由底注派生**
  = `底注 × 6`（叫分上限 3 × 地主两倍份数 = 无炸弹时最坏的一局），房主也可以直接指定
  （界面档位到 100 万，手改 JSON 更大；0 = 自动）。门槛只用于入局审核，不影响入局实收的
  押注数（仍是底注个）。手动值低于派生值会提示"输家可能赔不出"，但不拒绝保存
  ——高额桌与宽松桌都是房主的自由。不足则明确提示还差多少。
- **计分**：底分 = 叫分；倍数 = `2^(炸弹+火箭)` × 春天（或反春天）系数，统计在
  `DDZEngine`（`bombCount` / `landlordPlayCount` / `hasPlayed[]`），有 `DDZScoringTest` 覆盖。
  每人得失 = 底分 × 倍数 × 底注，地主两倍。倍数可能让单局远超底注，故支付为
  "池子 → 输家背包补足 → 仍然不足则少赔并提示"。
- **物品池**（`DDZSession.betStakes`）：入局时把**本桌底注**个筹码逐个收进池子，用 `split(1)` 取出，
  **附魔/自定义名等组件原样保留**，结算或退还时交还原物。绝不 `new ItemStack` 重建
  ——那会抹掉全部组件（曾经的缺陷：押附魔剑拿回白板剑）。
  **赔付必须先动池子、再动背包**（`pay`）：池子里的东西是入局时就收走的，动不了手脚；
  若反过来"先把押注退还给各家、再从背包取赔付"，池子就是空的，
  输家只要在结算前把筹码塞进箱子（或丢在地上）就一分不赔——押注形同虚设。
- **结算零和**：地主赢 → 退还自己那份 + 收下两农民的两份（净 +2），两农民各 -1；
  农民赢 → 各退还自己那份 + 地主赔付各 1 份（净 +1），地主 -2。总量守恒，
  不会像旧实现那样每局静默销毁 1 个物品。
- **判负（逃跑 / 超时）**：局中 Shift+右键离开、掉线、或每轮限时
  （`turnTimeoutTicks`，默认 30 秒）到点没动作 → 按判负结算：判负者赔**其余两家各
  「本桌门槛 / 2」**（奇数向下取整），复用 `pay` 的"池子优先"，然后解散牌局
  （其余两家的押注照常退还）。**局中走人不能只退押注**，否则就是"输了就跑、一分不赔"。
  触发在 `DDZSession.forfeit`，清扫在 `DDZTableManager`（每轮限时与配置锁 4 次/秒，
  空闲销毁每秒；`tickTurnTimeout` 用"阶段 + 该谁 + 叫分 + 上家出的牌 + 三家手牌总数"
  的轮次指纹判断换人）。
  **限时可以在玩法设置页用开关关掉**（开关写的就是 `turnTimeoutTicks = 0`；配置里只有这一个
  真相，档位里不再放"不限时"，免得出两处状态）。关掉后不判负，也不下发倒计时。
  `DDZTableConfigGameTest.forfeitPaysHalfEntryEach` / `turnTimeoutForfeits` /
  `turnTimeoutDisabledNeverForfeits` 与 `DDZSessionGameTest.midGameLeaveForfeitsToOthers`
  断言<b>金额守恒</b>（判负者净 -门槛、其余两家各 +门槛/2、总量不变）。
  `waitingLeaveRefundsOwnStake` 钉住另一半：**开局前**离开只退自己那份押注，牌局留着继续等人。

## 音频（音乐包：模组不含任何音频）

**模组 jar 里不放一个 `.ogg`**（`build.gradle` 的 `jar { exclude '**/*.ogg' }` 是发布侧兜底，
单测 `CustomAudioTest.modResourcesContainNoAudio` 挡住把音频放回 `resources` 的提交）。
音效与 BGM 全部来自**音乐包**：`config/crafty_cards/soundpacks/<包id>/pack.json + *.ogg`，
公开仓库的 `learning-soundpacks/` 提供三个 1.21.1 带音频的学习样本；模组 jar 仍不含音频。来源与授权风险见 `release-materials/AUDIO-RIGHTS.md`。

**包 id（= 目录名）由制作程序随机生成，玩家只填「显示名」与「作者」**：游戏里的音乐包列表
显示后两项，而 `activePack` 与权重引用（`pack:<包id>/<文件>`）用的是 id。不让玩家填 id 是因为
一个目录只放得下一份 `pack.json`——两个包共用 id 时（都取名 `my_pack` 最容易发生），后构建的
会把前一个的清单**顶掉**：那个包在游戏里"没声音了"，但文件还在、列表里还看得见，极难排查。
`soundpack_maker.py` 因此先看住目录再写（`claim_pack_dir`）：目录里是**别的显示名**的包就换一个
新 id（原包毫发无损），命令行显式指定的 id 则直接报错；`pick_free_pack_id` 只认"目录不存在"的
算空闲。例外是 `soundpacks/官方默认包/`——它用**手挑的中文 id**，与随机 id 无关，由人工维护
（制作程序也不接受中文 id），所以改它就是改目录名，要一并更新文档里的引用。

**音频键（`game/CustomAudio`）共 61 个 = 7 音效 + 6 BGM + 48 牌型语音**
（`pass` / `bgm_playing` / `bgm_clutch` / `bgm_rocket` / `dan3` / `shunzi` …）。
键名同时是配置字段名与音效 id（`InitSounds`）。

**BGM 不只看阶段，还跟着场上形势换**（`CustomAudio.bgmFor`，纯函数有单测）：出牌阶段里
**有人只剩 3 张以内**（`CLUTCH_CARDS`）→ `bgm_clutch`（残局紧张）；桌面最后一手是**王炸**
→ `bgm_rocket`（火箭没人压得住，所以它会持续到下一轮重新出牌，正是"过后"该有的长度）；
两者同时成立时**王炸优先**。结算仍按自己的胜负分曲。

**BGM 有回退链**（`CustomAudio.bgmChain`）：`bgm_rocket → bgm_clutch → bgm_playing`，
由 `ClientBgm` 依次找第一个"包里配了文件"的键，一个都没有才不播。
这条链是**给老音乐包的安全带**：没有它，"给游戏加了两个新 BGM 键"就会变成
"这些时刻背景音乐直接消失"（播放侧的规则是"这个键没候选就什么都不播"）。
`minCardsLeft()` 里 **0 不算**（空座位/已出完）：否则等人或旁观时也会响残局曲。

**注册 id 与资源包条目名必须同源（`CustomAudio.soundId`）——踩过一次大的**：注册（`InitSounds`）
用的是 `voice_<键>`（48 条语音）与 `<键>`（13 个槽位），而注入资源包的 `sounds.json` 顶层键必须
**逐字**与之相同（那份文件是按 id 配音频的）。曾经两边各写各的：注册写 `voice_<键>`、生成侧写裸键，
于是 48 条牌型语音**全部没有音频资源**——而且<b>不会退回原版音效</b>：配置里"这个键有候选"，
`ClientSounds` 照播那个没有资源的事件，结果是**纯静音**（用户看到的是"出牌没声音"，
日志里只有启动时一行 `Missing sound for event: crafty_cards:voice_dan1`，极易当成噪音略过）。
现在两边共用 `soundId()`，并由三道钉子守着：`CustomAudioTest.soundIdPrefixesVoicesOnly`（纯函数语义）、
`CustomAudioJsonTest.mainEntriesUseRegisteredSoundIds`（生成侧）、
`CCAudioGameTest.soundEventIdsMatchSoundId`（真实注册表里 61 个键逐一核对）。
另外 `ClientSounds.libraryHas()` 会问一句"当前加载的资源包里到底有没有这个事件"，
没有就退回原版音效并打一条只出现一次的告警——把"静默"变成"能听见、也能查"。

**排查"某个音效没声音"的标准动作**：看 `run/logs/latest.log` 里 `Missing sound for event:` 那批，
它们是**按注册表遍历**报出来的（启动与每次资源重载各一批），缺哪些、有没有缺，一目了然。

**加/改音频槽位要三处同步**：`CustomAudio.Slot` 枚举（含 `label` 中文名）、`InitSounds` 里的
**逐个显式注册**、`tools/soundpack_maker.py` 的键表（跨语言守卫 `makerLabelsMatchGameLabels`
会逐字比对中文名）。漏注册这次真的发生了：新加的 `bgm_clutch` / `bgm_rocket` 只进了枚举，
事件没注册——后果是这两首 BGM **永远不响**（`ClientBgm` 取到 null 直接返回，不报错、不崩溃、
连"缺音频"的日志都不会有，因为事件压根不存在），是
`CCAudioGameTest.soundEventIdsMatchSoundId` 遍历 61 个键核对注册表时当场抓住的。

**一个键可以有多个文件、各带整数权重（1~99）**，权重写进生成的 `sounds.json` 的 `weight`，
**随机由原版音效引擎完成**（`Sound.weight` 是 int，`WeighedSoundEvents` 按权重挑），
模组侧没有任何随机逻辑。权重写在**包的 `pack.json` 里**（`{file, weight}` 写法），
要调权重就改包——游戏侧不再提供逐条调权重的入口。

**播放优先级**（`ClientSounds`）：牌型语音键有候选 → 用它；否则通用 `play`/`bomb` 键；
都没有 → 原版音效。BGM 见上面的回退链（BGM 没有"原版音效"可退，全都没配才不播）。

**候选只有一个来源**：`CustomSoundConfig.resolve()` 逐键取**当前音乐包**声明的条目，
权重也用包内声明的值。游戏侧**没有任何"在游戏里直接改音频"的操作**——历史上曾经有过
「自备单文件」（把 .ogg 放进 `config/crafty_cards/sounds/` 再在界面里指派给某个音频键，
配合 `sounds.json` 里的 `localFiles` / `weights` 覆盖），已整条路径删除：两处都能改音频时，
排查问题先要问清"你到底改的哪边"，而且"游戏内指派"绕过了制作程序对包格式的把关。
旧配置里残留的 `localFiles` / `weights` 键被静默忽略（Gson 不认识的键不解析），不再生效。

**音乐包只由制作程序修改**：`tools/soundpack_maker.py`（tkinter 单体 GUI，`--cli` 是无界面模式）
负责转码 MP3→OGG、归类到音频键、写 `pack.json`（版本 2，`sounds: {键: [文件或 {file,weight}]}`）。
游戏侧只**读取与选用**音乐包，从不写回；界面上能改的只有三样：总开关、音量、选哪个包。
老版本 1 的包（`soundEffects`/`bgm` 两张表）仍可读，`version` 字段区分。

**注入资源包（`client/CustomSoundPack`）是唯一的 `sounds.json` 来源**：模组 jar 里既没有
`sounds.json` 也没有 `.ogg`，所以"高优先级包整体替换同名文件"那个坑不再存在——
但仍然只写"复制成功"的候选（否则会生成指向空气的条目 → 静默 + 日志告警）。
每次生成前会清空 `sounds_pack/assets/crafty_cards/sounds/custom/`，避免残留旧文件。
`reloadResourcePacks()` **只重跑已注册的资源包源**、不会重新触发 `AddPackFindersEvent`，
所以：①资源包必须**始终注入**（哪怕一个键都没配，否则"启动时没配、进游戏后再配"重载捡不到）；
②保存时必须显式调 `CustomSoundPack.regenerate()` 把新内容写进目录，重载才能读到。

## 解散路径必须逐个通知（踩过的坑）

**任何把会话从管理器移除的路径，都要先给"所有还会看这个界面的人"发一份 `sessionClosed`
快照**——包括不占座位的观战者，也包括**刚刚离开的那个人**。

`DDZSession.handleLeave` 曾经是"先清座位、再 disband"，而 `disband` 只遍历 `seats`：
离开者的客户端因此收不到任何终止快照、永久停在牌局界面上；同时会话已从管理器移除，
`BlockEvent.BreakEvent` 找不到会话、拆桌自然也没反应——玩家看到的就是
"牌被收回了但牌局关不掉、拆桌子也没用"。修法是**在清座位之前**先给离开者发
`buildSnapshot(player, "你离开了牌局", true)`。

排查这类问题的通用手法：客户端只认服务端下发的快照（`ClientSnapshotPolicy`），
所以"界面卡死"几乎总能归结为"服务端在某条早退/异常分支上少发了一份快照"，
顺着 `manager.remove` / `closed = true` 的每个调用点数一遍接收者即可。

**`handleLeave` 的三步顺序是有讲究的，别调换**（每一步都对应一次真实故障）：
1. 先发 `sessionClosed` 快照（理由如上）；
2. 再 `reclaimCard` + `returnBet` —— 两者都靠 `seatOf(uuid)` 找自己的位子，
   **清座位之后它们会直接早退**，离开者的押注就被静默吞掉（真正的物品损失）；
3. 最后才 `seats[seat] = null`，让后续 `broadcast` / `disband` 只处理还在座的人。

`disband` 遍历座位表退筹码时也必须 `if (p != null)`：离开者的位子已经清掉，
表里就有 null，`returnBet(null)` 会在 `PlayerLoggedOutEvent` 里抛 NPE，
把服务端线程带崩（表现是"玩家退出世界时崩溃"）。
`DDZSessionGameTest.midGameLeaveForfeitsToOthers` / `waitingLeaveRefundsOwnStake`
把这两条一起钉住了（单测覆盖不到——这段需要 `ServerPlayer`）。
**"开配置界面的人"也算一类要通知到的人**：他可能既没座位也没观战，
`PlayerLoggedOutEvent` 的清理条件与 `disband` 的接收者都得把他算上，否则他的配置界面会一直开着。
（GameTest 里验证不了下发本身：模拟玩家的连接不是真实客户端，发自定义 payload 会抛
`Payload ... may not be sent to the client!`，故这一段只能靠人眼在游戏里验收。）

## 音效触发时机（历史说明）

早期版本注册过自定义 SoundEvent 并附带 6 个 `.ogg`，但那些文件是 **0 字节占位符**
（播放会解码失败并刷日志）。后来改成"玩家自备音频"，再后来（当前）改成**音乐包**：
模组一个音频文件都不带，音频的来源与责任都在玩家/服务器管理员手上。

音效由**快照差异**驱动（服务端只发状态、不发音频指令）：比较前后两份快照判断
"开局发牌、叫分被抬高、有人出牌"，因此不会因旁观刷新之类的重复下发而连播。

## 下线功能时要成对处理（踩过的坑）

下线某个玩法时，**实体类型的注册与其客户端渲染器的注册必须一起处理**：
只留类型、去掉渲染器，会让旧存档里已存在的该实体会在渲染时抛
`NPE: ... "entityrenderer" is null` 并**崩客户端**（发生在 `LevelRenderer.renderLevel`
→ `EntityRenderDispatcher.shouldRender`，堆栈里看不到实体名，很难定位）。

`EntityCardDeck` 就踩了这个：牌堆**物品**已下线，但实体类型因为被卡牌翻面逻辑引用而
保留注册，渲染器却跟着一起注释掉了 → 旧世界一进就崩。结论是"保留类型"就必须"保留渲染器"。
同类问题也适用于物品/方块：注册表里留着但缺渲染资源，往往只在特定存档下才暴露。

## 1.21 数据包与资源约定（踩过的坑）

| 项 | 1.21 的正确写法 | 旧写法（不生效且无报错） |
|---|---|---|
| 配方目录 | `data/<ns>/recipe/` | `recipes/` |
| 掉落表目录 | `data/<ns>/loot_table/` | `loot_tables/` |
| 进度目录 | `data/<ns>/advancement/` | `advancements/` |
| 配方产物 | `"result": {"id": "ns:item", "count": N}` | `"item": ...`（1.20.5 起改名） |
| 网络动作枚举 | 只能往**末尾**追加（`writeEnum` 用 ordinal 编码） | 插在中间会让动作号错位 |
| 物品标签命名空间 | `c:dyes/black`（NeoForge 通用标签） | `forge:dyes/black`（1.21 已无此命名空间） |

子目录会被递归扫描（`recipe/blocks/x.json` 的 id 是 `ns:blocks/x`）。掉落表则**必须**放在
`loot_table/blocks/` 下——方块默认查询的就是 `ns:blocks/<方块名>`。

这些错误全部是**静默失效**：目录名写错 = 游戏根本不去扫；字段名/标签写错 = 加载时打一行
ERROR 后跳过。玩家侧表现为"合成不出来、破坏方块不掉落"却毫无提示，故由
`gametest/CCDataPackGameTest` 在运行时查询固定住（配方是否加载、掉落表是否存在、地毯碰撞箱高度）。

## 测试

```bash
.\gradlew.bat test              # 120 个单元测试（引擎/座位几何/可见性/HUD 布局/倒计时/音频键与 BGM 规则）
.\gradlew.bat runGameTestServer # 35 个 GameTest（牌桌放置与破坏、每桌配置与房主、下注与判负金额守恒、倒计时与限时开关、音频事件 id、快照可见性、整局流程、无筹码局）
python -m pytest tools         # 31 个测试：音乐包制作程序的纯逻辑（包 id、占用检测、键识别）
```

`gradlew build` **不跑 GameTest**（只跑单测 + 打包），所以要验牌桌那块必须单独跑
`runGameTestServer`——历史上就出现过"`build` 是绿的、GameTest 其实已经挂了"的情况。

**客户端跑着的时候，绝对不要再跑 `build` / `runGameTestServer`**（踩过一次真的崩了）：
开发环境的模组类是从 `build/classes/java/main` **这个目录**按需加载的，javac 重写 class 文件是
"先删再写"。正在加载 `ClientDDZData`（它引用 `TableConfigScreen`）的客户端恰好撞上那一瞬间，
就会抛 `NoClassDefFoundError`，而 **JVM 会把这个失败永久缓存** —— 客户端直接崩，
`WorldHandCards.render` → `ClientDDZData` 的堆栈里看不出真正原因（只有 `ClassNotFoundException`
指向一个"明明存在"的类）。顺序只能是：**改代码 → build → 再启动客户端**；
客户端在跑就等它退出（或先让玩家退出世界）再构建。
（另外一个历史坑：`gameTestServer` 以前与 `client` 共用 `run/`，跑一次测试就把 Player1 的
`run/logs/latest.log` 覆盖掉。现在它单独用 `run-gametest/`，见 `build.gradle`。）

单元测试里有一条**跨语言守卫**：`CustomAudioTest.makerLabelsMatchGameLabels` 会读
`tools/soundpack_maker.py`，把里面的中文名与 `CustomAudio.label()` 逐字比对——两边的说法必须
一致（BGM 那组的「BGM：」前缀就是区分"结算音效 vs 结算 BGM"的依据）。

测试类路径上**没有 Minecraft 的类**，故可单测的类必须不引用 MC 类型（`DDZEngine`、`DDZGeom`、`DDZVisibility`、`HudPlayedLayout` 都是纯 Java）。

`DDZEngine` 是纯 Java，所以**规则可以单独编译出来跑**：把 `DDZEngine`/`DDZCardType`/`DDZGamePhase`
三个文件连同自写探针放进临时目录 `javac` 一下即可。改牌型判定时值得这么做——
枚举"飞机各种带牌形态"之类的手牌形状并随机采样几十万手，再加几千局随机对局查守恒，
比只跑单测更容易发现"某个形状被误判"：单测只能钉住你**想得到**的那几个形状。
（`3333 444 55 6` 被判成飞机带对就是这么漏掉的，见下。）

测试覆盖：发牌、叫地主、所有牌型判断（含 10-J-Q-K-A 最大五连压 9-10-J-Q-K）、出牌验证、炸弹/火箭互压、大小王、胜负判定、快照 NBT 序列化往返

## 约定

- `ItemCardCovered` 不出现在创造模式标签页
- `EntityStacked.MAX_STACK_SIZE = 54`（从 52 扩展以容纳大小王）
- 牌堆默认创建 54 张牌（含大小王）
