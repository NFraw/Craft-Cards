# 炸金花 HUD 布局探针报告

生成时间：2026-10-21 21:16:55

| 项 | 值 |
|---|---|
| 阶段 | 24 |
| 分辨率 | 4 |
| 检查点 | 1356 |
| PASS | 1356 |
| FAIL | 0 |
| WARN | 0 |

## 检查了什么

- **actions-clear-crosshair** — 整条操作行位于准星安全区上方，包含底衬与焦点范围
- **cards-bottom-anchored** — 卡牌下沿恒等于 屏高 − 底边距（默认 30，与斗地主 hudBottomMargin 一致）
- **card-model-bounds** — 按完整模型宽10.2、高16计算牌框及实绘缩放，不把中间牌面宽9当成完整模型宽
- **cards-do-not-overlap** — 三张牌互不重叠（间距 > 牌宽）
- **cards-inside-viewport** — 整片手牌左右各留 ≥ 8px
- **cards-centered** — 三张牌整体水平居中
- **cards-fit-height** — 卡牌上沿 ≥ 60，让开标题/阶段/局势/反馈四行
- **message-clears-header** — 反馈行让开顶部三行（标题/阶段/局势）
- **message-above-hint** — 反馈行在提示行上方
- **message-gap-shared** — 反馈→提示→选项的间距等于 HudOptionBar 的共享常量（两游戏同一套）
- **hint-above-options** — 提示行在选项行上方且不压（斗地主叫分阶段原来是反的，已一并纠正）
- **options-above-hand-info** — 选项行在手牌信息上方且不压
- **hint-above-hand-info** — 无选项行的阶段，提示行直接让开手牌信息
- **hand-info-above-cards** — 手牌信息不压卡牌
- **option-count** — 排布项数与阶段应有项数一致
- **option-uniform-width** — 等宽项：与斗地主叫分选项同一个 HudOptionBar
- **option-inside-viewport** — 选项行含焦点框左右各 2px 都在视口内
- **option-no-overlap** — 相邻选项不重叠
- **option-min-hit-width** — 每项至少 10px 可点
- **option-label-fully-visible** — 每个标签完整显示（不被 plainSubstrByWidth 截断）
- **settled-header-above-rows** — 结算标题/获胜行不压第一行
- **settled-block-centered** — 结算内容块垂直居中（与斗地主同一套），不贴顶留大片空白
- **settled-rows-clear-hint** — 最后一行让开底部那行关闭提示
- **settled-row-fits-name** — 行高 ≥ 14，名字行在行内
- **settled-text-column-clear** — 右侧文字列不压牌列且可用宽 ≥ 40
- **settled-two-lines-inside-row** — 行高 ≥ 22 时两行文字都在行内
- **settled-one-line-inside-row** — 行高 < 22 时退成一行且在行内
- **settled-tight-net-visible** — 一行模式先裁牌型、不裁净得失数字（数字必须留在屏幕上）
- **settled-hint-inside-viewport** — 底部提示行在视口内
- **settled-title-inside-viewport** — 结算标题在视口内

## 与斗地主的竖向语法对照

| 分辨率 | 斗地主选项行 y | 炸金花选项行 y | 差值 | 斗地主项宽 | 炸金花项宽 | 卡牌下沿 |
|---|---|---|---|---|---|---|
| 320×180 | 78.8 | 78.8 | 0.0 | 22.5 | 22.5 | 153.6 |
| 427×240 | 105.0 | 105.0 | 0.0 | 30.0 | 30.0 | 213.8 |
| 640×360 | 157.5 | 157.5 | 0.0 | 45.0 | 45.0 | 326.3 |
| 960×540 | 236.3 | 236.3 | 0.0 | 67.5 | 67.5 | 489.4 |

提示行、反馈行的位置两边都由 `HudOptionBar.hintY/messageY` 从各自的选项行 y 推出（提示在选项上方 13px、反馈再上方 13px），所以竖向语法一致；两游戏现在都先换算到高度320的设计画布，再应用同一操作锚点。表中坐标、宽度已乘回缩放系数，单位为GUI像素。旧报告直接把GUI高度代入斗地主公式，14/28px差值不是当时的实机差距；本探针通过也不代表实机功能测试通过。

## 逐组合结果

| 阶段 | 分辨率 | 检查 | FAIL | WARN |
|---|---|---|---|---|
| waiting | 320×180 | 19 | 0 | 0 |
| waiting-reason | 320×180 | 19 | 0 | 0 |
| playing-dark-r1 | 320×180 | 19 | 0 | 0 |
| playing-dark-r3 | 320×180 | 19 | 0 | 0 |
| playing-revealed1 | 320×180 | 19 | 0 | 0 |
| playing-revealed2 | 320×180 | 19 | 0 | 0 |
| playing-revealed3 | 320×180 | 19 | 0 | 0 |
| playing-bigblind | 320×180 | 19 | 0 | 0 |
| playing-cap | 320×180 | 19 | 0 | 0 |
| selecting-cards | 320×180 | 12 | 0 | 0 |
| target-2 | 320×180 | 19 | 0 | 0 |
| target-3 | 320×180 | 19 | 0 | 0 |
| target-6 | 320×180 | 19 | 0 | 0 |
| target-longname | 320×180 | 19 | 0 | 0 |
| settled-win-2 | 320×180 | 8 | 0 | 0 |
| settled-lose-2 | 320×180 | 8 | 0 | 0 |
| settled-win-3 | 320×180 | 8 | 0 | 0 |
| settled-lose-3 | 320×180 | 8 | 0 | 0 |
| settled-win-4 | 320×180 | 8 | 0 | 0 |
| settled-lose-4 | 320×180 | 8 | 0 | 0 |
| settled-win-5 | 320×180 | 8 | 0 | 0 |
| settled-lose-5 | 320×180 | 8 | 0 | 0 |
| settled-win-6 | 320×180 | 8 | 0 | 0 |
| settled-lose-6 | 320×180 | 8 | 0 | 0 |
| waiting | 427×240 | 19 | 0 | 0 |
| waiting-reason | 427×240 | 19 | 0 | 0 |
| playing-dark-r1 | 427×240 | 19 | 0 | 0 |
| playing-dark-r3 | 427×240 | 19 | 0 | 0 |
| playing-revealed1 | 427×240 | 19 | 0 | 0 |
| playing-revealed2 | 427×240 | 19 | 0 | 0 |
| playing-revealed3 | 427×240 | 19 | 0 | 0 |
| playing-bigblind | 427×240 | 19 | 0 | 0 |
| playing-cap | 427×240 | 19 | 0 | 0 |
| selecting-cards | 427×240 | 12 | 0 | 0 |
| target-2 | 427×240 | 19 | 0 | 0 |
| target-3 | 427×240 | 19 | 0 | 0 |
| target-6 | 427×240 | 19 | 0 | 0 |
| target-longname | 427×240 | 19 | 0 | 0 |
| settled-win-2 | 427×240 | 8 | 0 | 0 |
| settled-lose-2 | 427×240 | 8 | 0 | 0 |
| settled-win-3 | 427×240 | 8 | 0 | 0 |
| settled-lose-3 | 427×240 | 8 | 0 | 0 |
| settled-win-4 | 427×240 | 8 | 0 | 0 |
| settled-lose-4 | 427×240 | 8 | 0 | 0 |
| settled-win-5 | 427×240 | 8 | 0 | 0 |
| settled-lose-5 | 427×240 | 8 | 0 | 0 |
| settled-win-6 | 427×240 | 8 | 0 | 0 |
| settled-lose-6 | 427×240 | 8 | 0 | 0 |
| waiting | 640×360 | 19 | 0 | 0 |
| waiting-reason | 640×360 | 19 | 0 | 0 |
| playing-dark-r1 | 640×360 | 19 | 0 | 0 |
| playing-dark-r3 | 640×360 | 19 | 0 | 0 |
| playing-revealed1 | 640×360 | 19 | 0 | 0 |
| playing-revealed2 | 640×360 | 19 | 0 | 0 |
| playing-revealed3 | 640×360 | 19 | 0 | 0 |
| playing-bigblind | 640×360 | 19 | 0 | 0 |
| playing-cap | 640×360 | 19 | 0 | 0 |
| selecting-cards | 640×360 | 12 | 0 | 0 |
| target-2 | 640×360 | 19 | 0 | 0 |
| target-3 | 640×360 | 19 | 0 | 0 |
| target-6 | 640×360 | 19 | 0 | 0 |
| target-longname | 640×360 | 19 | 0 | 0 |
| settled-win-2 | 640×360 | 8 | 0 | 0 |
| settled-lose-2 | 640×360 | 8 | 0 | 0 |
| settled-win-3 | 640×360 | 8 | 0 | 0 |
| settled-lose-3 | 640×360 | 8 | 0 | 0 |
| settled-win-4 | 640×360 | 8 | 0 | 0 |
| settled-lose-4 | 640×360 | 8 | 0 | 0 |
| settled-win-5 | 640×360 | 8 | 0 | 0 |
| settled-lose-5 | 640×360 | 8 | 0 | 0 |
| settled-win-6 | 640×360 | 8 | 0 | 0 |
| settled-lose-6 | 640×360 | 8 | 0 | 0 |
| waiting | 960×540 | 19 | 0 | 0 |
| waiting-reason | 960×540 | 19 | 0 | 0 |
| playing-dark-r1 | 960×540 | 19 | 0 | 0 |
| playing-dark-r3 | 960×540 | 19 | 0 | 0 |
| playing-revealed1 | 960×540 | 19 | 0 | 0 |
| playing-revealed2 | 960×540 | 19 | 0 | 0 |
| playing-revealed3 | 960×540 | 19 | 0 | 0 |
| playing-bigblind | 960×540 | 19 | 0 | 0 |
| playing-cap | 960×540 | 19 | 0 | 0 |
| selecting-cards | 960×540 | 12 | 0 | 0 |
| target-2 | 960×540 | 19 | 0 | 0 |
| target-3 | 960×540 | 19 | 0 | 0 |
| target-6 | 960×540 | 19 | 0 | 0 |
| target-longname | 960×540 | 19 | 0 | 0 |
| settled-win-2 | 960×540 | 8 | 0 | 0 |
| settled-lose-2 | 960×540 | 8 | 0 | 0 |
| settled-win-3 | 960×540 | 8 | 0 | 0 |
| settled-lose-3 | 960×540 | 8 | 0 | 0 |
| settled-win-4 | 960×540 | 8 | 0 | 0 |
| settled-lose-4 | 960×540 | 8 | 0 | 0 |
| settled-win-5 | 960×540 | 8 | 0 | 0 |
| settled-lose-5 | 960×540 | 8 | 0 | 0 |
| settled-win-6 | 960×540 | 8 | 0 | 0 |
| settled-lose-6 | 960×540 | 8 | 0 | 0 |

