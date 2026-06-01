const fs = require('fs');
const path = require('path');

const FILE_PATH = path.join(__dirname, '../starc.txt');
const ERROR_JSON_PATH = path.join(__dirname, '../starc_errors.json');

// ANSI 颜色辅助
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  bold: '\x1b[1m'
};

function main() {
  if (!fs.existsSync(FILE_PATH)) {
    console.error(`${colors.red}${colors.bold}错误：未找到 starc.txt 文件在路径: ${FILE_PATH}${colors.reset}`);
    process.exit(1);
  }

  console.log(`${colors.cyan}${colors.bold}=== 开始对 starc.txt 进行格式体检 ===${colors.reset}\n`);

  const content = fs.readFileSync(FILE_PATH, 'utf-8');
  const diagnostics = [];
  const warnings = [];

  // 1. 验证首行和第二行指令
  const firstLines = content.split(/\r?\n/).slice(0, 2);
  if (firstLines[0] !== '#separator:tab') {
    diagnostics.push({
      line: 1,
      type: 'HEADER_ERROR',
      message: `首行必须是 "#separator:tab"，当前是 "${firstLines[0]}"`
    });
  }
  if (firstLines[1] !== '#html:true') {
    diagnostics.push({
      line: 2,
      type: 'HEADER_ERROR',
      message: `第二行必须是 "#html:true"，当前是 "${firstLines[1]}"`
    });
  }

  // 2. 编写基于字符流的状态机，解析 TSV 并精准追踪物理行号
  let pos = 0;
  let line = 1;
  let cards = [];
  const totalLength = content.length;

  while (pos < totalLength) {
    // 跳过每条记录开头的换行符
    while (pos < totalLength && (content[pos] === '\r' || content[pos] === '\n')) {
      if (content[pos] === '\n') line++;
      pos++;
    }
    if (pos >= totalLength) break;

    const startLine = line;
    const fields = [];
    let currentField = '';
    let inQuote = false;
    let cardEnded = false;
    const cardStartPos = pos;

    while (pos < totalLength && !cardEnded) {
      const char = content[pos];

      if (inQuote) {
        // 如果处于双引号包裹中
        if (char === '"') {
          if (content[pos + 1] === '"') {
            // 转义的双双引号 ""
            currentField += '"';
            pos += 2;
            continue;
          } else {
            // 包裹结束的单双引号 "
            inQuote = false;
            pos++;
            // 校验：在引号结束后，必须紧接着制表符或换行符或文件结束
            const nextChar = content[pos];
            if (pos < totalLength && nextChar !== '\t' && nextChar !== '\r' && nextChar !== '\n') {
              diagnostics.push({
                line: line,
                type: 'TSV_SYNTAX_ERROR',
                message: `字段双引号闭合后，出现了非分隔符字符: "${nextChar}"，请检查双引号转义是否正确。`
              });
            }
            continue;
          }
        } else {
          if (char === '\n') line++;
          currentField += char;
          pos++;
        }
      } else {
        // 未在双引号包裹中
        if (char === '"' && currentField.length === 0) {
          // 在字段开头遇到双引号，进入包裹模式
          inQuote = true;
          pos++;
        } else if (char === '\t') {
          // 遇到制表符，字段结束
          fields.push(currentField);
          currentField = '';
          pos++;
        } else if (char === '\r' || char === '\n') {
          // 遇到换行，卡片记录结束
          fields.push(currentField);
          currentField = '';
          cardEnded = true;
          // 注意：不要在此时消费换行符，留给外层循环或下一步处理，以精确统计行号
        } else {
          currentField += char;
          pos++;
        }
      }
    }

    // 处理文件末尾未闭合的最后一个字段
    if (!cardEnded) {
      fields.push(currentField);
    }

    const endLine = line;
    const cardRaw = content.substring(cardStartPos, pos);

    // 将卡片加入解析结果中
    cards.push({
      startLine,
      endLine,
      fields,
      raw: cardRaw
    });
  }

  console.log(`已成功解析出 ${colors.bold}${cards.length}${colors.reset} 张问答卡片，正在深入校验其内部结构...`);

  // 3. 对每一张卡片进行详细内容校验
  cards.forEach((card, index) => {
    // 忽略前两行卡片（系统头部）
    if (card.startLine <= 2 && card.fields.length <= 1) {
      return;
    }

    const cardId = `卡片 #${index + 1} (物理行 L${card.startLine}~L${card.endLine})`;

    // 校验 A：TSV 字段数量校验
    if (card.fields.length !== 2) {
      diagnostics.push({
        line: card.startLine,
        type: 'FIELD_COUNT_ERROR',
        message: `${cardId}：TSV 字段数不等于 2。当前解析出 ${card.fields.length} 个字段。可能是因为中间有多余的制表符，或者字段外围没有正确使用双引号包裹，导致内部换行被误判为新行。`
      });
      return; // 字段数量不对，跳过后续需要依赖正反面的内部校验
    }

    const [question, answer] = card.fields;

    // 校验 B：主卡片文本中的 [[概念]] 标记闭合性校验
    const conceptRegex = /\[\[(.*?)\]\]/g;
    const textToScan = answer;
    
    // 检查是否有未闭合的 [[
    let unclosedIndex = -1;
    let scanPos = 0;
    while ((scanPos = textToScan.indexOf('[[', scanPos)) !== -1) {
      const closeIdx = textToScan.indexOf(']]', scanPos);
      if (closeIdx === -1 || (textToScan.indexOf('[[', scanPos + 2) !== -1 && textToScan.indexOf('[[', scanPos + 2) < closeIdx)) {
        diagnostics.push({
          line: card.startLine,
          type: 'CONCEPT_UNCLOSED_ERROR',
          message: `${cardId}：发现未闭合的概念标记 "[[", 请确保每一对双括号完整闭合。`
        });
        break;
      }
      scanPos += 2;
    }

    // 提取正文引用的所有概念
    const referencedConcepts = [];
    let match;
    while ((match = conceptRegex.exec(textToScan)) !== null) {
      const conceptName = match[1].trim();
      if (conceptName) {
        referencedConcepts.push(conceptName);
      }
    }

    // 校验 C：提取并校验 HTML 注释中的 JSON 块
    const commentRegex = /<!--([\s\S]*?)-->/g;
    let commentMatch;
    const comments = [];
    while ((commentMatch = commentRegex.exec(answer)) !== null) {
      comments.push({
        rawInner: commentMatch[1],
        startIdx: commentMatch.index,
        endIdx: commentMatch.index + commentMatch[0].length
      });
    }

    // 检查是否有类似 <!-- 但没有正确闭合的情况
    let openCommentIndex = -1;
    let cScan = 0;
    while ((cScan = answer.indexOf('<!--', cScan)) !== -1) {
      if (answer.indexOf('-->', cScan) === -1) {
        diagnostics.push({
          line: card.startLine,
          type: 'COMMENT_UNCLOSED_ERROR',
          message: `${cardId}：发现未闭合的 HTML 注释 "<!--"，导致整个卡片反面解析受损。`
        });
      }
      cScan += 4;
    }

    // 筛选出 concept JSON 注释块
    const conceptJsonComments = comments.filter(c => {
      const inner = c.rawInner;
      const hasItems = inner.includes('"items"') || inner.includes('""items""');
      const hasFollowUps = inner.includes('"追问"') || inner.includes('""追问""') || inner.includes('"QA"') || inner.includes('""QA""');
      return hasItems || hasFollowUps;
    });

    if (conceptJsonComments.length === 0) {
      // 并非所有卡片都需要概念，这只属于信息，不报错。但如果有 referencedConcepts 却没 JSON，那就是缺失定义！
      if (referencedConcepts.length > 0) {
        diagnostics.push({
          line: card.startLine,
          type: 'MISSING_CONCEPT_BLOCK',
          message: `${cardId}：正文引用了概念 ${JSON.stringify(referencedConcepts)}，但未在卡片内提供任何概念 JSON 注释块 \`<!-- { ... } -->\`。`
        });
      }
      return;
    }

    conceptJsonComments.forEach(comment => {
      const rawText = comment.rawInner.trim();
      const startBrace = rawText.indexOf('{');
      const endBrace = rawText.lastIndexOf('}');

      if (startBrace < 0 || endBrace <= startBrace) {
        diagnostics.push({
          line: card.startLine,
          type: 'JSON_STRUCT_ERROR',
          message: `${cardId}：概念注释块内部没有找到闭合的大括号 \`{ ... }\`。`
        });
        return;
      }

      const jsonSubstr = rawText.substring(startBrace, endBrace + 1);
      
      // 在 TSV 文件中，JSON 的双引号以 "" 的形式存储。解析前必须先还原为单一的双引号 "
      // 注意：要小心避免把真的双引号误换成别的。
      const normalizedJson = jsonSubstr.replace(/""/g, '"');

      let jsonObject;
      try {
        jsonObject = JSON.parse(normalizedJson);
      } catch (err) {
        diagnostics.push({
          line: card.startLine,
          type: 'JSON_PARSE_ERROR',
          message: `${cardId}：JSON 语法解析失败。报错: "${err.message}"。请检查是否存在未转义的双引号、多余/缺失逗号或括号不闭合。`
        });
        return;
      }

      // 详细校验 JSON 的键值结构
      const definedConceptTitles = [];

      // 1. items 数组校验
      if (jsonObject.hasOwnProperty('items')) {
        const items = jsonObject.items;
        if (!Array.isArray(items)) {
          diagnostics.push({
            line: card.startLine,
            type: 'JSON_SCHEMA_ERROR',
            message: `${cardId}：JSON 中的 "items" 必须是数组格式。`
          });
        } else {
          const seenIds = new Set();
          items.forEach((item, itemIdx) => {
            const itemLoc = `items[${itemIdx}]`;
            
            // 校验 id
            if (!item.hasOwnProperty('id') || item.id === undefined || item.id === null) {
              diagnostics.push({
                line: card.startLine,
                type: 'JSON_SCHEMA_ERROR',
                message: `${cardId}：${itemLoc} 缺少 "id" 字段。`
              });
            } else {
              const itemIdStr = String(item.id).trim();
              if (seenIds.has(itemIdStr)) {
                diagnostics.push({
                  line: card.startLine,
                  type: 'DUPLICATE_CONCEPT_ID',
                  message: `${cardId}：定义了重复的概念 ID "${itemIdStr}"，已被 Kotlin 解析器丢弃。`
                });
              }
              seenIds.add(itemIdStr);
            }

            // 校验 title
            if (!item.hasOwnProperty('title') || !String(item.title).trim()) {
              diagnostics.push({
                line: card.startLine,
                type: 'JSON_SCHEMA_ERROR',
                message: `${cardId}：${itemLoc} 缺少非空 "title" 字段。`
              });
            } else {
              definedConceptTitles.push(String(item.title).trim());
            }

            // 校验 问答 (extractQuestionAnswer)
            const hasQ = item.hasOwnProperty('q');
            const hasA = item.hasOwnProperty('a');
            let itemQ = "";
            let itemA = "";

            if (hasQ || hasA) {
              itemQ = String(item.q || "").trim();
              itemA = String(item.a || "").trim();
            } else {
              // 自动提取除 id 和 title 之外的第一个键
              const otherKeys = Object.keys(item).filter(k => k !== 'id' && k !== 'title');
              if (otherKeys.length > 0) {
                itemQ = otherKeys[0].trim();
                itemA = String(item[otherKeys[0]] || "").trim();
              }
            }

            if (!itemA) {
              diagnostics.push({
                line: card.startLine,
                type: 'CONCEPT_EMPTY_ANSWER',
                message: `${cardId}：概念卡片 "${item.title || item.id}" 的回答 (answer) 为空，将被 App 解析器过滤掉！`
              });
            }
          });
        }
      }

      // 2. QA 或 追问 数组校验
      const followUpKey = jsonObject.hasOwnProperty('追问') ? '追问' : (jsonObject.hasOwnProperty('QA') ? 'QA' : null);
      if (followUpKey) {
        const followUps = jsonObject[followUpKey];
        if (!Array.isArray(followUps)) {
          diagnostics.push({
            line: card.startLine,
            type: 'JSON_SCHEMA_ERROR',
            message: `${cardId}：JSON 中的 "${followUpKey}" 必须是数组格式。`
          });
        } else {
          const seenQAIds = new Set();
          followUps.forEach((qa, qaIdx) => {
            const qaLoc = `${followUpKey}[${qaIdx}]`;

            // 校验 id
            if (!qa.hasOwnProperty('id') || qa.id === undefined || qa.id === null) {
              diagnostics.push({
                line: card.startLine,
                type: 'JSON_SCHEMA_ERROR',
                message: `${cardId}：${qaLoc} 缺少 "id" 字段。`
              });
            } else {
              const qaIdStr = String(qa.id).trim();
              if (seenQAIds.has(qaIdStr)) {
                diagnostics.push({
                  line: card.startLine,
                  type: 'DUPLICATE_QA_ID',
                  message: `${cardId}：定义了重复的追问 ID "${qaIdStr}"，已被 Kotlin 解析器丢弃。`
                });
              }
              seenQAIds.add(qaIdStr);
            }

            // 校验 QA 的问答有效性
            const hasQ = qa.hasOwnProperty('q');
            const hasA = qa.hasOwnProperty('a');
            let qaQ = "";
            let qaA = "";

            if (hasQ || hasA) {
              qaQ = String(qa.q || "").trim();
              qaA = String(qa.a || "").trim();
            } else {
              const otherKeys = Object.keys(qa).filter(k => k !== 'id');
              if (otherKeys.length > 0) {
                qaQ = otherKeys[0].trim();
                qaA = String(qa[otherKeys[0]] || "").trim();
              }
            }

            if (!qaQ) {
              diagnostics.push({
                line: card.startLine,
                type: 'QA_EMPTY_QUESTION',
                message: `${cardId}：追问 (ID: ${qa.id}) 问题字段为空，将被 App 解析器丢弃。`
              });
            }
            if (!qaA) {
              diagnostics.push({
                line: card.startLine,
                type: 'QA_EMPTY_ANSWER',
                message: `${cardId}：追问 (ID: ${qa.id}) 答案字段为空，将被 App 解析器丢弃。`
              });
            }
          });
        }
      }

      // 对照校验 1：正文中引用的每个 [[概念]] 必须在 items 中有定义
      referencedConcepts.forEach(ref => {
        if (!definedConceptTitles.includes(ref)) {
          diagnostics.push({
            line: card.startLine,
            type: 'MISSING_CONCEPT_DEFINITION',
            message: `${cardId}：正文引用了 [[${ref}]]，但 JSON items 中没有定义以 "${ref}" 为 title 的概念！`
          });
        }
      });

      // 对照校验 2：在 items 中定义的概念，如果没有被正文 [[引用]]，输出 Warning
      definedConceptTitles.forEach(def => {
        if (!referencedConcepts.includes(def)) {
          warnings.push({
            line: card.startLine,
            type: 'ORPHAN_CONCEPT_DEFINITION',
            message: `${cardId}：在 items 中定义了概念 "${def}"，但在主卡片正文中没有用 [[${def}]] 进行引用。`
          });
        }
      });
    });
  });

  // 4. 统计并输出报告
  console.log(`\n${colors.cyan}${colors.bold}=== 体检报告 ===${colors.reset}`);
  
  if (warnings.length > 0) {
    console.log(`\n${colors.yellow}${colors.bold}⚠️ 发现了 ${warnings.length} 个警告：${colors.reset}`);
    warnings.forEach(w => {
      console.log(`  [行 ${w.line}] [${w.type}] ${colors.yellow}${w.message}${colors.reset}`);
    });
  }

  if (diagnostics.length > 0) {
    console.log(`\n${colors.red}${colors.bold}❌ 发现了 ${diagnostics.length} 个严重格式错误：${colors.reset}`);
    diagnostics.forEach(d => {
      console.log(`  [行 ${d.line}] [${d.type}] ${colors.red}${colors.bold}${d.message}${colors.reset}`);
    });

    // 写入 JSON 报告供 AI 精密解析
    const report = {
      timestamp: new Date().toISOString(),
      file: FILE_PATH,
      errorCount: diagnostics.length,
      warningCount: warnings.length,
      errors: diagnostics,
      warnings: warnings
    };
    fs.writeFileSync(ERROR_JSON_PATH, JSON.stringify(report, null, 2), 'utf-8');
    console.log(`\n${colors.magenta}已生成结构化错误日志 starc_errors.json，供 AI 提取并自动修复。${colors.reset}`);
    process.exit(1);
  } else {
    console.log(`\n${colors.green}${colors.bold}🎉 恭喜！未检测到任何严重格式错误。starc.txt 格式完美无缺，可以顺利导入 Anki 并被 Listener 解析！${colors.reset}`);
    if (fs.existsSync(ERROR_JSON_PATH)) {
      fs.unlinkSync(ERROR_JSON_PATH); // 校验成功，清除陈旧的错误日志
    }
    process.exit(0);
  }
}

main();
