# RAG 质量升级说明

## 目标

本轮 P1 把 Erise-AI 的 RAG 能力从“能检索”升级为“可控、可评测、可解释”。

重点改动：

1. 新增 Query Rewrite / Query Expansion 模块。
2. 新增强引用守卫（Strict Citation Guard），当私有资料证据不足时直接降级回答，不允许伪造“依据某文档”。
3. 新增答案-引用一致性校验，用于离线评测和请求日志观测。
4. 新增离线评测脚本与最小样例数据。

## 核心实现

### 1. Query Rewrite / Query Expansion

新增服务：

- `src/app/services/query_rewrite_service.py`

能力：

- 清洗口语化问法和冗余前缀
- 将“这份文档 / 这个附件 / 发送给你的文件”等泛指表达收敛成更稳定的检索词
- 自动补充摘要、概述、主要内容、步骤、处理流程等扩展词
- 对 OCR / PDF / TXT / DOCX / RAG 等领域词做扩展

接入点：

- `src/app/services/rag_service.py`

当前会把原始问句、重写问句和扩展问句一起参与：

- 向量检索
- 关键词检索
- 联网回退查询

### 2. 严格引用模式

新增服务：

- `src/app/services/citation_guard_service.py`

规则：

- 当回答来源为 `PRIVATE_KNOWLEDGE` 且证据不足时，直接降级
- 当前默认判定依据包括：
  - 私有引用条数不足
  - 检索置信度过低
  - 命中片段质量不足

降级后不会继续调用模型生成“像是有依据”的回答，而是返回谨慎说明，并附上目前仅能确认的相关片段。

### 3. 答案-引用一致性校验

新增能力：

- 对回答内容做 token 级覆盖率计算
- 检测是否存在“根据文档 / 依据附件 / 文档显示”一类强依据表述
- 输出一致性结果：
  - `coverageRatio`
  - `consistencyPassed`
  - `claimDetected`
  - `reason`

当前阶段：

- 一致性校验会进入请求日志的响应负载
- 一致性校验会进入离线评测结果
- 为避免前台噪音，目前不会把一致性提醒直接展示成用户报错

## 可观测性

`chat_service` 现会把以下 RAG 质量信息写入 `ai_request_log.response_payload_json`：

- `rewrittenQueries`
- `rewriteHints`
- `evidenceGuard`
- `consistencyCheck`

结合 P0 中新增的统一日志字段，可以直接追踪：

- 这次请求是否用了 query rewrite / query expansion
- 为什么触发了弱证据降级
- 答案和引用的覆盖率有多高

## 离线评测

新增脚本：

- `scripts/rag_eval.py`

新增样例数据：

- `scripts/eval_data/rag_eval_minimal.json`

当前输出指标：

- `hitRateAt1`
- `hitRateAtTopK`
- `averageCitationCoverage`
- `weakEvidenceRatio`

## 运行命令

### Python 回归测试

```bash
cd D:\EriseAi\AiAssistant
python -m unittest discover -s tests -v
```

### 阶段 smoke

```bash
cd D:\EriseAi\AiAssistant
python scripts\ai_stage_smoke.py
```

### 离线评测

```bash
cd D:\EriseAi\AiAssistant
python scripts\rag_eval.py
```

### Java backend 测试

```bash
cd D:\EriseAi\erise-ai-backend
mvn -gs ..\.mvn-settings.xml -s ..\.mvn-settings.xml test
```

## 评测结果示例

基于最小样例数据的一次本地运行结果：

- `hitRateAt1 = 1.0`
- `hitRateAtTopK = 1.0`
- `averageCitationCoverage = 0.7619`
- `weakEvidenceRatio = 0.0`

## 备注

本轮没有大改现有接口路径，主要变更集中在：

- `rag_service`
- `chat_service`
- 新增 rewrite / citation guard 服务
- 新增离线评测脚本与样例数据

后续如果进入下一阶段，可以继续把：

- 一致性校验结果接入后台看板
- 严格引用与 Query Rewrite 做成用户可调策略
- 评测集扩展成真实业务样本
