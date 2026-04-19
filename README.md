# Erise-AI

Erise-AI 是一个面向个人或轻量团队的项目知识库系统，核心能力围绕以下场景展开：

- 项目管理
- 文件上传、预览、下载与历史跟踪
- 文档阅读、编辑与解析
- 统一搜索与知识检索
- 管理后台
- AI / RAG 能力接入

当前仓库由以下主要模块组成：

- `erise-ai-ui`：Vue 3 + Vite 前端
- `erise-ai-backend`：Spring Boot 业务后端与统一网关入口
- `AiAssistant`：Python AI 服务，负责聊天、RAG、文档解析、Agent、外部编排等能力
- `deploy/`：Nginx、vLLM、LiteLLM、n8n 等部署叠加配置

## 运行链路

默认链路：

`erise-ai-ui -> Nginx -> erise-ai-backend -> AiAssistant -> Model Provider`

其中：

- 前端通过 Nginx 访问后端接口
- Java 后端负责业务对象、鉴权、项目/文件/文档等主流程
- `AiAssistant` 对外暴露 `/internal/ai/chat/...` 内部接口，供 Java 服务调用
- 模型层可切换为 Ollama、vLLM、LiteLLM，具体取决于当前 compose 叠加方式和环境变量

## 支持矩阵

下表按“仓库当前实际落地情况”整理：

| 能力         | Dev                                           | Prod                                           | 当前状态             | 说明                                                                                        |
| ------------ | --------------------------------------------- | ---------------------------------------------- | -------------------- | ------------------------------------------------------------------------------------------- |
| Ollama       | 支持，默认开发链路                            | 根 `docker-compose.yml` 未内置 Ollama 服务     | 已落地               | 开发态默认 `cloud -> ollama`，适合作为本地推理默认方案。                                    |
| LiteLLM      | 支持，`deploy/litellm/docker-compose.dev.yml` | 支持，`deploy/litellm/docker-compose.vllm.yml` | 实验性               | 已有 dev/prod overlay，但不是根 compose 默认路径，当前主要作为模型网关叠加方案。            |
| vLLM         | 支持，`deploy/vllm/docker-compose.dev.yml`    | 支持，`deploy/vllm/docker-compose.yml`         | 实验性               | 已有 direct vLLM overlay 和默认模型链路，但仍属于需要显式叠加的高性能部署路径。             |
| n8n          | 支持，`deploy/n8n/docker-compose.dev.yml`     | 支持，`deploy/n8n/docker-compose.yml`          | 已落地（外围自动化） | 已接入 webhook 外发、重试、幂等和签名，但不是内嵌工作流引擎。                               |
| Unstructured | 支持                                          | 支持                                           | 已落地               | 已进入 `AiAssistant/requirements.txt`，并作为主文档解析器启用，失败时回退到 legacy parser。 |

补充说明：

- “已落地”表示默认安装链路或明确部署链路已经打通，文档和代码基本一致。
- “实验性”表示仓库已有实现与 overlay，但不是默认主路径，后续仍可能继续收敛配置、命名和运维规范。
- “规划中”能力不在上表列出；当前 README 仅描述仓库里已经存在的实现。

## Dev 启动

推荐使用 Docker 开发链路，本机无需额外安装 Java、Node 或 Python 运行环境。

### 1. 基础准备

1. 复制 `.env.dev.example` 为 `.env.dev`
2. 按需填写以下配置：
   - `INTERNAL_API_KEY`
   - `OPENAI_API_KEY` / `DEEPSEEK_API_KEY`（如果需要远程模型）
   - `WEB_SEARCH_PROVIDER` 与 `TAVILY_API_KEY`（如果需要联网搜索）

### 2. 默认开发链路：Ollama

这是当前仓库最完整、最直接的开发启动方式。

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml up --build
```

默认行为：

- `cloud` 直连 `ollama`
- `ollama-init` 自动预热聊天模型和 embedding 模型
- 适合作为日常本地开发与联调入口

### 3. 开发态切到 vLLM

如果你希望在开发环境直接验证 vLLM：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml -f deploy/vllm/docker-compose.dev.yml --profile vllm up --build
```

注意：

- 这是本地推理链路，不是默认开发路径
- 需要保证 `VLLM_MODEL_CODE`、`VLLM_SERVED_MODEL_NAME`、`DEFAULT_MODEL_CODE` 保持一致
- 需要满足 GPU / 显存要求

### 4. 开发态切到 LiteLLM

如果你希望通过 LiteLLM 统一模型入口：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml -f deploy/litellm/docker-compose.dev.yml up --build
```

该模式下：

- `cloud` 连接 LiteLLM
- LiteLLM 再转发到 Docker 网络中的 Ollama

### 5. 开发态叠加 n8n

如果要验证外围自动化链路：

```bash
docker compose --env-file .env.dev -f deploy/n8n/docker-compose.dev.yml up -d
```

### 6. 停止开发环境

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

## Dev 访问地址

- 统一入口：`http://localhost:8088`
- 前端开发口：`http://localhost:5173`
- Java 后端健康检查：`http://localhost:8080/actuator/health`
- Python AI 服务健康检查：`http://localhost:8081/internal/ai/chat/health`
- n8n（如果已启动）：`http://localhost:5678`

## Prod 启动

生产基础栈使用根目录的 `docker-compose.yml`。它默认包含：

- MySQL
- Redis
- MinIO
- Qdrant
- Java Backend
- AiAssistant
- UI
- Nginx

也就是说，根生产 compose 默认并不直接启用 Ollama、vLLM、LiteLLM、n8n，这些能力都需要额外叠加 overlay。

### 1. 基础生产栈

1. 复制 `.env.example` 为 `.env`
2. 填写数据库、鉴权、模型 API 等必要配置
3. 启动基础生产栈：

```bash
docker compose -f docker-compose.yml --env-file .env up -d
```

### 2. prod模式叠加 vLLM

```bash
docker compose \
  -f docker-compose.yml \
  -f deploy/vllm/docker-compose.yml \
  --env-file .env \
  --env-file deploy/vllm/.env.prod \
  --profile vllm \
  up -d
```

说明：

- `deploy/vllm/docker-compose.yml` 是当前推荐的生产 overlay
- `deploy/vllm/docker-compose.prod.yml` 仍保留为兼容旧命令的别名

### 3. prod模式叠加 LiteLLM + vLLM

```bash
docker compose \
  -f docker-compose.yml \
  -f deploy/vllm/docker-compose.yml \
  -f deploy/litellm/docker-compose.vllm.yml \
  --env-file .env \
  --env-file deploy/vllm/.env.prod \
  --env-file deploy/litellm/litellm.env.example \
  --profile vllm \
  --profile litellm \
  up -d
```

该模式下：

- `cloud` 只连接 LiteLLM
- LiteLLM 再转发到 `vllm` 和 `vllm-embed`

### 4. prod模式叠加 n8n

```bash
docker compose -f deploy/n8n/docker-compose.yml --env-file .env up -d
```

注意：

- 当前 `deploy/n8n/docker-compose.yml` 的 `env_file` 指向 `../../.env.dev`
- 这意味着它虽然有生产 compose 文件，但配置仍带有明显开发痕迹
- 因此 README 把 n8n 标记为“已落地（外围自动化）”，但不把它描述成完全收敛的生产标准化方案

## 能力边界

### Ollama

- 当前是最稳的开发默认链路
- 适合本地联调与轻量模型测试
- 根生产 compose 未内置 Ollama 服务

### LiteLLM

- 当前作为模型网关 overlay 使用
- 开发态对接 Ollama，生产态主要对接 vLLM
- 已有 compose 与模型映射，但不是默认主路径

### vLLM

- 已有 dev/prod overlay
- 当前适合作为高性能本地推理方案
- 部署时除了 `VLLM_BASE_URL`，还必须保证模型编码链路一致：
  - `VLLM_MODEL`
  - `VLLM_SERVED_MODEL_NAME`
  - `VLLM_MODEL_CODE`
  - `DEFAULT_MODEL_CODE`

### n8n

- 当前是 webhook 驱动的外围自动化能力
- 已接入事件发送、重试、幂等键、HMAC 签名头
- 不是内嵌 workflow engine，不在同步聊天主链路中

### Unstructured

- 当前是 `AiAssistant` 的主文档解析器
- 已写入 `AiAssistant/requirements.txt`
- `AiAssistant/Dockerfile` 与 `AiAssistant/Dockerfile.dev` 也已补齐 PDF / OCR 所需系统依赖
- 主解析失败后，会回退到 legacy parser

### Agent Graph

- `/internal/ai/chat/agents/run` 使用 `AiAssistant/src/app/services/agent_graph_service.py` 作为 Agent 编排入口
- `langgraph` 已写入 `AiAssistant/requirements.txt`
- 当前仍保留明确降级路径：
  - 依赖缺失 -> 记录日志 -> 切线性编排
  - 运行失败 -> 记录日志 -> 切线性编排
  - 线性编排失败 -> 回退到普通 chat service

## 文档解析能力

当前文档解析链路以 `AiAssistant/src/app/services/file_extract_service.py` 为核心：

- 主解析器：`unstructured`
- 支持类型：`pdf`、`docx`、`txt`、`md/markdown`
- fallback：legacy parser
- OCR 相关系统依赖已进入 Docker 镜像

当前已知现实边界：

- 大 PDF 文件解析仍然较慢
- 复杂版式 OCR 仍可能不稳定
- 长文档在上下文预算受限时，仍可能被截断

## n8n 集成边界

当前 n8n 只用于外围自动化，而不是内建编排引擎，主要覆盖：

- 审批通知
- 通知分发
- 定时巡检
- 异常告警
- 周报分发

当前已具备：

- `n8n_event_log` 记录外发事件
- 重试机制
- 幂等键
- HMAC 签名头

当前未具备：

- AiAssistant 内嵌工作流运行时
- 完整的回调驱动状态闭环
- 分布式补偿 / 编排引擎

## 主要页面示意

<table>
  <tr>
    <td><img src="./artifacts/login-page.png" alt="登录页" width="300px" /></td>
    <td><img src="./artifacts/workspace-page.png" alt="工作台" width="300px" /></td>
  </tr>
  <tr>
    <td><img src="./artifacts/project-page.png" alt="项目页" width="300px" /></td>
    <td><img src="./artifacts/Konwledge-page.png" alt="知识库页" width="300px" /></td>
  </tr>
  <tr>
    <td><img src="./artifacts/AiChat-page.png" alt="AI 聊天页" width="300px" /></td>
    <td><img src="./artifacts/admin-page.png" alt="管理后台" width="300px" /></td>
  </tr>
</table>

## 相关目录

- [deploy/litellm/README.md](./deploy/litellm/README.md)
- [deploy/vllm/README.md](./deploy/vllm/README.md)
- [deploy/n8n/README.md](./deploy/n8n/README.md)
- [AiAssistant/requirements.txt](./AiAssistant/requirements.txt)
<!-- - [AiAssistant/src/app/services/agent_graph_service.py](./AiAssistant/src/app/services/agent_graph_service.py) -->
