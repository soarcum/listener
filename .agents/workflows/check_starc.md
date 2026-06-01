---
description: check_starc
---

# starc.txt 导入格式体检与自动修复 (Anki Import Checker & Fixer)

此工作流用于对 `starc.txt` 文档进行体检，找出所有的 TSV 包裹错误、双引号转义不当、概念标记未闭合、JSON 损坏以及概念对照缺失等严重错误，并且会在检测到错误时自动触发 AI 智能无损修复机制，直至校验完全通过。

## 步骤 1：执行文档格式体检
调用 Node.js/Bun 执行开发好的深度校验引擎，对 `starc.txt` 进行全面扫描。
// turbo
```powershell
Write-Host "正在运行格式体检脚本..."
bun scripts/validate_starc.js
```

## 步骤 2：AI 自动解析报告并执行无损修复
当且仅当步骤 1 报错且生成了 `starc_errors.json` 时，AI 引擎将自动接管，按物理行号高精度编辑文件。
> [!NOTE]
> 1. AI 将自动读取 `starc_errors.json`。
> 2. 定位到受影响的物理行号，分析其语法损坏情况。
> 3. 对不符合 TSV 转义、JSON 大括号不闭合或未包裹的行进行微创重构。
> 4. 修复完成后将自动再次执行体检以验证结果，实现自闭环直至 0 错误。
