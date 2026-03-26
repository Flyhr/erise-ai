# Erise-AI 数据库详细设计文档（V1）

## 1. 设计原则
- 数据库：MySQL 8.x
- 字符集：utf8mb4
- 表前缀：`ea_`
- 主键：bigint
- 所有核心表统一包含审计字段与逻辑删除字段

## 2. 数据域划分
1. 用户与认证域
2. 项目域
3. 文件域
4. 文档域
5. 标签域
6. 搜索与知识块域
7. AI 会话与引用域
8. 任务域
9. 审计与系统域

## 3. 核心表
### 用户与认证
- `ea_user`
- `ea_user_profile`
- `ea_user_setting`
- `ea_user_login_log`

### 项目
- `ea_project`
- `ea_project_member`（预留）

### 文件
- `ea_file`
- `ea_file_version`
- `ea_file_preview`
- `ea_file_parse_task`

### 文档
- `ea_document`
- `ea_document_content`
- `ea_document_version`

### 标签
- `ea_tag`
- `ea_project_tag_rel`
- `ea_file_tag_rel`
- `ea_document_tag_rel`

### 搜索与知识块
- `ea_knowledge_chunk`
- `ea_knowledge_index_job`
- `ea_search_history`

### AI
- `ea_ai_session`
- `ea_ai_message`
- `ea_ai_citation`
- `ea_ai_tool_call_log`
- `ea_ai_model_config`

### 审计与系统
- `ea_task`
- `ea_audit_log`
- `ea_operation_log`
- `ea_system_config`

## 4. 关键关系
- 用户 1:N 项目
- 项目 1:N 文件
- 项目 1:N 文档
- 文件/文档 1:N 知识块
- AI 会话 1:N AI 消息
- AI 消息 1:N 引用

## 5. 重点字段建议
### ea_file
- owner_user_id
- project_id
- file_name/file_ext/mime_type/file_size
- storage_provider/storage_bucket/storage_key
- checksum_md5/checksum_sha256
- parse_status/preview_status/index_status

### ea_document
- owner_user_id
- project_id
- title/summary
- doc_status
- latest_version_no
- editor_type

### ea_knowledge_chunk
- owner_user_id
- project_id
- source_type/source_id/source_title
- chunk_index/chunk_text/page_no/section_path
- embedding_ref/index_status

## 6. 索引建议
- 常规索引：owner_user_id、project_id、status、created_at、updated_at
- 组合索引：
  - 文件列表：`(project_id, deleted, created_at desc)`
  - 文档列表：`(project_id, doc_status, updated_at desc)`
- 唯一索引：username、email 等

## 7. 设计结论
数据库要坚持“主数据清晰、知识块独立、AI 数据解耦、审计可追踪”的原则，为后端、搜索与 AI 能力留好扩展空间。
