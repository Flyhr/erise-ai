# RAG 质量升级说明

## 目标

本轮升级将 Erise-AI 的 RAG 链路从“可检索”推进到“可调优、可解释、可重建”。

本次已经落地的重点包括：

1. 查询改写从纯规则增强为“规则 + LLM”双层方案
2. 检索从“Qdrant dense + Java 侧 BM25”演进为“Qdrant dense+sparse 一体化存储”
3. 召回融合权重支持按查询类型动态切换
4. 排序从本地 reranker 扩展为“LLM 重排序优先，cross-encoder 回退”
5. 旧的 Java 内部 AI 检索入口退役，并补充废弃说明
6. 支持在测试环境中直接强制重建旧的 dense-only collection

## 当前架构

### 1. 查询前处理

入口：

- `src/app/services/query_rewrite_service.py`
- `src/app/services/retrieval_llm_service.py`

当前行为：

- 保留用户原始问题不变
- 检索侧先做规则清洗、意图识别、关键词聚焦
- 如果 LLM 可用，再补充：
  - 问题意图
  - 更完整的专业检索语句
  - 关键词扩展
  - 语义扩展
  - 检索策略画像 `BALANCED / KEYWORD_HEAVY / VECTOR_HEAVY`

### 2. 索引存储

入口：

- `src/app/services/rag_service.py`
- `src/app/services/sparse_vector_service.py`

当前 Qdrant collection 结构：

- dense 向量名：`dense`
- sparse 向量名：`sparse`

每个 chunk 写入时会同时包含：

- dense embedding
- sparse term vector
- 统一 payload 元数据

关键 payload 字段：

- `source_type`
- `source_id`
- `source_name`
- `source_url`
- `source_version`
- `chunk_num`
- `chunk_id`
- `chunk_hash`
- `task_id`
- `metadata`
- `page_no`
- `section_path`

### 3. 召回与排序

当前召回链路：

1. dense 检索
2. sparse 检索
3. 按权重做融合
4. 进入 LLM 重排序
5. 如果 LLM 重排序不可用，则回退到 cross-encoder
6. 如果本地 reranker 也不可用，则回退到保守归一化排序

当前默认权重：

- 平衡查询：`0.7 dense + 0.3 sparse`
- 报错 / 日志 / 代码类查询：`0.5 dense + 0.5 sparse`

## 与旧方案的区别

### 已废弃的旧链路

以下链路已经不再服务 AI 助手主检索：

- Python -> Java `/internal/v1/knowledge/retrieve`
- Java 内部 AI 专用 BM25 召回入口

相关历史文件仍保留，但已在文件顶部或代码块中添加中文废弃说明，用于：

- 回滚排障
- 历史接口追溯
- 避免团队误接回旧链路

### 仍保留的 Java 稀疏能力

`erise-ai-backend` 中的 `SparseKnowledgeSupport` 没有删除，原因是：

- 它仍被工作台页面搜索复用
- 它对 AI 主检索已经废弃
- 它对页面搜索暂时仍然有效

## 旧 collection 自动迁移策略

当前策略已经调整为：

- 如果检测到 Qdrant collection 是旧的 dense-only 结构
- 且当前环境里的历史数据被视为可丢弃测试数据
- 系统将直接删除并按新的 dense+sparse 结构重建 collection

对应代码：

- `src/app/services/rag_service.py`

这个行为用于开发 / 测试环境快速完成索引层升级，不再要求手工保留旧测试数据。

## 全量重建结果

最近一次本地实跑结果：

- 启动环境：`docker compose --env-file .env -f docker-compose.yml up -d`
- 删除旧测试 collection
- 对现有文件资产重新触发索引链

执行结果：

- collection：`kb_chunks_ollama_dev`
- dense 维度：`768`
- sparse modifier：`idf`
- 当前点数：`2231`

补充说明：

- 上述结果来自当时的本地测试配置
- 统一环境后的官方默认 collection 名称应以 `.env` / `.env.example` 中的 `QDRANT_KB_COLLECTION` 为准

文件资产重建结果：

- `createFile.txt` -> `SUCCESS`
- `郝源毕业设计说明书-草稿2.docx` -> `SUCCESS`
- `txt_sample_file_5MB.txt` -> `SUCCESS`
- `pdf_sample_file_25MB.pdf` -> `SUCCESS`

说明：

- 其中 PDF 大文件重建过程中出现过一次 MySQL `Lock wait timeout exceeded`
- 后端自动重试后最终成功

## 验证命令

### Python 全量回归

```bash
cd D:\EriseAi\AiAssistant
python -m unittest discover -s tests -v
```

### Java 编译验证

```bash
cd D:\EriseAi\erise-ai-backend
mvn -gs ..\.mvn-settings.xml -s ..\.mvn-settings.xml -DskipTests compile
```

### 查看 Qdrant collection 结构

```powershell
Invoke-RestMethod -Headers @{ 'api-key' = 'dev-qdrant-key' } `
  http://localhost:6333/collections/kb_chunks_ollama_dev | ConvertTo-Json -Depth 12
```

### 查看 collection 点数

```powershell
Invoke-RestMethod -Headers @{ 'api-key' = 'dev-qdrant-key' } `
  http://localhost:6333/collections/kb_chunks_ollama_dev/points/count `
  -Method Post -ContentType 'application/json' -Body '{}' | ConvertTo-Json -Depth 8
```

## 后续建议

1. 如果要彻底统一检索体系，可以继续把页面搜索也迁移到 Qdrant sparse/dense 统一方案。
2. 可以为大文件索引任务增加更显式的数据库锁冲突重试与任务恢复提示。
3. 可以继续扩大离线评测集，覆盖更多“口语化问题 -> 专业检索语句”的真实业务样本。
