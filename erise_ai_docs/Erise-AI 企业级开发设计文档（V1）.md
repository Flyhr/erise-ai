# Erise-AI 企业级开发设计文档（V1）

## 1. 项目定位
Erise-AI 是一个私有垂直化项目知识库管理系统，面向个人用户或轻量团队，围绕“项目 + 文件 + 文档 + 搜索 + AI 助手”建立统一的知识沉淀、结构化管理、检索与复用空间。

系统支持：
- 多类型文件上传、预览、下载
- 在线文档编辑（图片、代码块、PDF 预览等）
- 标签分类、历史浏览
- 管理员后台（独立界面）
- 基于知识库内容的 AI 助手

## 2. 建设目标
### 2.1 业务目标
- 以项目为单位组织知识资产
- 支持文件与文档统一沉淀
- 支持关键词搜索与语义检索
- 支持 AI 严格基于知识库回答

### 2.2 技术目标
- 前后端分离
- 主体业务后端与 AI 微服务解耦
- API、数据库、命名、异常与权限模型统一
- 可从轻量部署平滑演进到企业级部署

## 3. 角色模型
- 普通用户：管理自己的项目、文件、文档、AI 会话
- 管理员：管理用户、项目、文件、任务、AI 配置、日志与系统参数

## 4. 总体架构
```text
erise-ai-ui        前端应用
erise-ai-backend   主体后端（认证/用户/项目/文件/文档/搜索/任务/管理/WebSocket）
erise-ai-cloud     AI 微服务（Spring AI + RAG + Tool Calling + MCP）
MySQL              结构化数据
Redis              缓存/会话/任务状态
MinIO              对象存储
Vector Store       向量检索
Nginx              统一入口
```

## 5. 部署方式建议
### 5.1 推荐方案
- 本地开发：前端可本机运行，后端与基础组件优先 Docker Compose
- 联调/测试：全量 Docker Compose
- 轻量生产：Docker Compose + Nginx + 持久化卷 + 备份策略
- 中后期：迁移 Kubernetes

### 5.2 为什么优先 Docker / Docker Compose
- 适合多组件系统统一编排
- 降低开发、测试、生产环境差异
- 更适合个人开发者或轻量团队
- 后续迁移 Kubernetes 成本更低

## 6. 技术栈建议
### 前端
- Node.js 24 LTS
- Vue 3
- Vite
- Vue Router
- Axios
- Element Plus
- Pinia
- TypeScript
- Tiptap / pdf.js / Monaco

### 后端主体
- Java 21
- Spring Boot
- Spring Security
- MyBatis-Plus
- Maven
- MySQL / Redis
- WebSocket
- OpenAPI / Knife4j

### AI 微服务
- Spring Boot
- Spring AI
- OpenAI 兼容模型
- Embedding 模型
- RAG
- Function Calling / MCP

## 7. 模块职责
### 主体后端模块
- auth：认证与 token
- user：用户与设置
- project：项目管理
- file：上传、预览、下载、解析任务
- document：在线文档与版本
- tag：统一标签
- search：综合搜索
- task：异步任务与重试
- history：最近访问与搜索历史
- admin：管理后台接口
- editor：WebSocket 与编辑器状态

### AI 微服务模块
- rag：知识入库、检索、重排、引用
- tools：文件/搜索/项目/SQL 工具
- mcp：外部工具接入
- provider：模型供应商抽象

## 8. 编码规范
- Controller 只接参并返回结果
- Service 负责业务流程、权限校验、事务控制
- Mapper 只负责数据库访问
- Entity 不直接返回前端
- DTO 作为入参，VO 作为出参

## 9. API 规范
- 前缀统一：`/api/v1`
- 响应结构统一：`code/message/data`
- 分页结构统一：`records/pageNum/pageSize/total/totalPages`
- 统一错误码分段：400/401/403/404/409/500/510/520/530

## 10. 数据库设计原则
- 表前缀：`ea_`
- 主键：`bigint`
- 通用审计字段：`created_by/created_at/updated_by/updated_at/deleted`
- 文件、文档、知识块、AI 会话拆域治理

## 11. 搜索与 AI 设计
- 支持关键词搜索、语义搜索、混合检索
- AI 必须严格基于知识库内容回答
- 输出必须带 citations
- 无依据时拒答或降级回答
- AI 工具能力必须受白名单、权限与审计约束

## 12. V1 推荐边界
优先落地：
- 用户认证
- 项目管理
- 文件上传/预览/下载
- 在线文档编辑
- 搜索
- 基于项目知识的 AI 问答
- 管理后台基础能力

## 13. 结论
Erise-AI 的 V1 最重要的是先把“项目—文件—文档—搜索—AI 引用回答”这条主链路做实，再逐步扩展到 MCP、SQL 工具和更高级的 Agent 能力。
