# 子概念复习功能实施计划

## 目标

- 在主卡背面支持结构化声明多个“子概念/解释卡”。
- 复习主卡时，先完成主卡正反面回忆，再按顺序追问到期子概念。
- 子概念拥有独立的本地间隔重复状态。
- 子概念没有完成或答错时，限制主卡最终评分，避免主卡被误判为掌握。
- 第一版不创建真实 Anki 子卡，先采用“主卡背面嵌入结构化数据 + App 本地调度”的方案。

## 推荐卡片格式

主卡背面保留普通答案文本，子概念放在 HTML 注释中的 JSON 块里：

```html
最开始项目使用 Vue3 和 Three.js 直接开发，Vue3 是声明式状态管理，而 Three.js 是命令式对象管理。
这直接带来了 3 个问题：[[状态同步易出错]]、[[资源生命周期容易失控]]、[[渲染层代码过重]]。

<!-- ankilistener:concepts:v1
{
  "items": [
    {
      "id": "state-sync-error",
      "title": "状态同步易出错",
      "q": "在 Vue 和 Three.js 混合开发中，为什么直接同步状态容易导致[[状态同步易出错]]？",
      "a": "因为业务数据变化后，需要手动将 Vue 的响应式状态同步到 Three.js 对象上，这种命令式同步代码分散且容易遗漏。"
    }
  ]
}
-->
```

约定：

- `id` 必须在同一张主卡内稳定且唯一，后续本地调度依赖它。
- `title` 用于 UI 展示和日志。
- `q` 是子概念提问文本。
- `a` 是子概念答案文本。
- 未来如需升级格式，使用 `ankilistener:concepts:v2`，避免破坏旧卡。

## 阶段 1：数据模型与解析

- [ ] 新增 `ConceptCard` 数据模型。
  - 建议文件：`app/src/main/java/com/ankilistener/app/data/ConceptCardModels.kt`
  - 字段：`id`、`title`、`question`、`answer`、`sourceNoteId`、`sourceOrd`。
- [ ] 新增解析工具 `ConceptCardParser`。
  - 建议文件：`app/src/main/java/com/ankilistener/app/util/ConceptCardParser.kt`
  - 从 `card.back` 原始 HTML 中匹配：
    - `<!-- ankilistener:concepts:v1`
    - 到最近的 `-->`
  - 用 `org.json.JSONObject` / `JSONArray` 解析，避免手写 JSON 解析。
- [ ] 解析失败时不要中断主卡复习。
  - 记录日志。
  - 返回空列表。
  - UI 不显示子概念流程。
- [ ] 在 `HtmlUtils` 中新增清理函数。
  - 建议函数：`removeAnkiListenerConceptBlocks(html: String): String`
  - 用于主卡背面显示和 TTS，避免把 JSON 注释内容读出来或显示出来。
- [ ] 给解析器补基础单元测试。
  - 正常解析 1 个子概念。
  - 正常解析多个子概念。
  - 无子概念块时返回空列表。
  - JSON 损坏时返回空列表并记录错误。
  - 重复 `id` 时忽略后者或自动记录错误，第一版建议忽略后者。

## 阶段 2：本地子概念调度

- [ ] 新增本地调度模型 `ConceptReviewState`。
  - 字段建议：
    - `key`
    - `dueAt`
    - `intervalDays`
    - `easeFactor`
    - `lastEase`
    - `reviewCount`
    - `lapseCount`
    - `updatedAt`
- [ ] 调度 key 使用：

```text
noteId:ord:conceptId
```

- [ ] 新增 `ConceptScheduleRepository`。
  - 建议文件：`app/src/main/java/com/ankilistener/app/data/ConceptScheduleRepository.kt`
  - 第一版可用 `SharedPreferences` 保存 JSON 字符串。
  - 后续如状态变多，再迁移到 Room 或文件 JSON。
- [ ] 实现 `isDue(key, now)`。
  - 无状态的子概念默认到期。
  - `dueAt <= now` 时到期。
- [ ] 实现子概念评分更新。
  - `Again`：`dueAt = now + 10 分钟`，`intervalDays = 0`，`lapseCount + 1`。
  - `Hard`：`dueAt = now + max(1 天, intervalDays * 1.2)`，`easeFactor - 0.15`。
  - `Good`：`dueAt = now + max(1 天, intervalDays * easeFactor)`。
  - `Easy`：`dueAt = now + max(3 天, intervalDays * easeFactor * 1.3)`，`easeFactor + 0.15`。
  - `easeFactor` 下限建议 `1.3`。
- [ ] 增加调度日志。
  - 每次进入主卡时记录子概念总数、到期数。
  - 每次评分后记录旧状态、新状态。

## 阶段 3：复习状态机改造

- [ ] 扩展 `ReviewState`。
  - 当前：`FRONT`、`BACK`、`FINISHED`、`LOADING`
  - 建议新增：
    - `CONCEPT_FRONT`
    - `CONCEPT_BACK`
- [ ] 在 `ReviewViewModel` 中新增子概念运行态。
  - `allConceptsForCurrentCard`
  - `dueConceptQueue`
  - `currentConceptIndex`
  - `currentConcept`
  - `conceptReviewResultForCurrentCard`
- [ ] 主卡正面流程保持不变。
- [ ] 主卡背面流程调整。
  - 用户触发显示答案后，先显示/朗读主卡背面。
  - 主卡背面朗读完成或用户继续操作后，进入子概念流程。
  - 如果没有到期子概念，直接允许主卡评分。
- [ ] 子概念流程。
  - `CONCEPT_FRONT`：显示/朗读子概念 `q`。
  - 用户触发显示答案。
  - `CONCEPT_BACK`：显示/朗读子概念 `a`。
  - 用户用 Again / Hard / Good / Easy 给子概念评分。
  - 更新本地调度。
  - 进入下一个到期子概念。
  - 全部结束后回到主卡 `BACK`，允许主卡最终评分。
- [ ] 防止用户绕过子概念直接评分主卡。
  - 当存在未完成到期子概念时，主卡评分手势不调用 `repository.answerCard`。
  - 给出轻提示，例如“还有 2 个概念待复习”。

## 阶段 4：主卡评分约束

- [ ] 记录本轮子概念最低评分。
  - `Again` 最低。
  - `Hard` 次低。
  - `Good` / `Easy` 算通过。
- [ ] 主卡最终评分规则。
  - 任意子概念 `Again`：主卡强制 `Again`，或禁止用户选择 `Good/Easy`。
  - 任意子概念 `Hard` 且无 `Again`：主卡最高只能 `Hard`。
  - 全部子概念 `Good/Easy`：主卡可正常评分。
- [ ] 第一版推荐更明确的策略。
  - 子概念 `Again` 后，主卡自动按 `Again` 提交。
  - 子概念最低为 `Hard` 时，如果用户选 `Good/Easy`，实际提交 `Hard`。
  - 日志中记录用户原始选择和实际提交 ease。

## 阶段 5：UI 与交互

- [ ] 在 `ReviewScreen` 中支持子概念显示。
  - `CONCEPT_FRONT` 显示：
    - 子概念进度，例如 `概念 1/3`
    - `title`
    - `question`
  - `CONCEPT_BACK` 显示：
    - 子概念问题
    - 分隔线
    - 子概念答案
- [ ] 主卡背面显示时隐藏 JSON 注释块。
  - 使用 `HtmlUtils.removeAnkiListenerConceptBlocks` 后再 `parseHtml`。
- [ ] TTS 文本也隐藏 JSON 注释块。
  - `getBackTtsText(card)` 先清理概念块。
- [ ] 手势复用现有逻辑。
  - `CONCEPT_FRONT`：`SHOW_ANSWER` 进入 `CONCEPT_BACK`。
  - `CONCEPT_BACK`：`ANSWER_*` 更新子概念调度。
  - `PLAY_TTS` 根据当前状态重读当前问题或答案。
- [ ] 增加轻量提示。
  - 进入子概念流程时提示“开始复习 3 个相关概念”。
  - 子概念结束后提示“相关概念复习完成”。

## 阶段 6：TTS 预取

- [ ] `prefetchUpcoming()` 中加入子概念 TTS 预取。
  - 解析未来卡片的子概念。
  - 只预取到期子概念的 `q` 和 `a`。
- [ ] 更新 `PrefetchStatus`。
  - 第一版可以不增加 UI 字段，只把子概念文本纳入预取。
  - 后续再显示概念缓存数量。
- [ ] 避免解析成本过高。
  - 当前预取数量较小，直接解析即可。
  - 后续如卡片很大，再考虑缓存解析结果。

## 阶段 7：设置项

- [ ] 在设置页增加“子概念复习”开关。
  - 默认开启。
  - 关闭后只显示主卡，忽略所有嵌入子概念。
- [ ] 增加“只复习到期子概念”设置。
  - 默认开启。
  - 关闭时每次主卡都追问所有子概念，适合调试或强化复习。
- [ ] 增加 `Again` 延迟设置。
  - 默认 10 分钟。
  - 可选：5 分钟、10 分钟、30 分钟、1 天。

## 阶段 8：边界情况

- [ ] 主卡没有子概念：完全沿用旧流程。
- [ ] 子概念 JSON 解析失败：沿用旧流程，记录日志。
- [ ] 子概念 `id` 改名：视为新子概念，旧调度状态保留但不再使用。
- [ ] 主卡 noteId 或 ord 变化：视为新主卡，子概念重新开始调度。
- [ ] 子概念答案为空：该子概念不进入队列并记录日志。
- [ ] 子概念问题为空：使用 `title` 生成兜底问题，例如“解释一下：状态同步易出错”。
- [ ] 用户中途跳过主卡：不更新未完成子概念状态，主卡按当前 skip/bury 逻辑处理。
- [ ] 用户 undo：第一版只恢复主卡导航，不回滚子概念本地调度；日志中明确记录。

## 阶段 9：测试与验收

- [ ] 构造测试卡 1：无子概念。
  - 验收：流程与当前版本一致。
- [ ] 构造测试卡 2：1 个子概念。
  - 验收：主卡背面后追问 1 次子概念。
  - 子概念评分后才能给主卡评分。
- [ ] 构造测试卡 3：3 个子概念。
  - 验收：按 JSON 顺序依次追问。
  - 进度显示正确。
  - TTS 顺序正确。
- [ ] 构造测试卡 4：有未到期子概念。
  - 验收：未到期子概念被跳过。
- [ ] 构造测试卡 5：子概念 `Again`。
  - 验收：主卡最终不能被评为 `Good/Easy`，实际提交为 `Again` 或被强制限制。
- [ ] 构造测试卡 6：JSON 坏格式。
  - 验收：App 不崩溃，主卡正常复习。
- [ ] 运行本地构建。
  - 命令：`./gradlew assembleDebug`
- [ ] 在真机或模拟器验证手势。
  - 单击显示答案。
  - 左滑 Again。
  - 下滑 Hard。
  - 右滑 Good。
  - 上滑 Easy。
  - 双击重读 TTS。

## 阶段 10：后续演进

- [ ] 支持从 `[[概念]]` 自动生成子概念骨架。
  - 当背面有 `[[概念]]` 但没有 JSON 时，提示缺少解释卡。
- [ ] 支持独立 Anki 子卡模式。
  - 子卡作为真实 Anki note 存在。
  - 通过标签或字段关联主卡。
  - App 主流程中调用子卡，但 deck 列表中不单独展示它们。
- [ ] 支持导出/备份本地子概念调度状态。
- [ ] 支持更接近 FSRS 的本地调度算法。
- [ ] 支持子概念掌握度统计。
  - 每张主卡的子概念通过率。
  - 最常失败概念列表。
  - 最近到期概念数量。

## 第一版推荐提交范围

- [ ] `ConceptCardModels.kt`
- [ ] `ConceptCardParser.kt`
- [ ] `ConceptScheduleRepository.kt`
- [ ] `HtmlUtils.kt`
- [ ] `ReviewViewModel.kt`
- [ ] `Screens.kt`
- [ ] `SettingsRepository.kt`
- [ ] `SettingsScreen.kt`
- [ ] 必要单元测试文件

## 第一版完成定义

- 主卡背面能嵌入 `ankilistener:concepts:v1` JSON。
- App 能解析出子概念，并在主卡背面之后按顺序追问。
- 子概念有独立本地到期时间。
- 子概念评分会影响主卡最终评分。
- 没有子概念的卡不受影响。
- 坏格式子概念不会导致崩溃。
- Debug 构建通过。
