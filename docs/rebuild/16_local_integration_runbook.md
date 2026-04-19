# 16. 本地联调手册

- 版本：前端闭环与 AI/RAG 联调批次
- 日期：2026-04-18
- 适用范围：本地 Docker 开发环境、前端闭环验收、AI/RAG/索引联调、管理后台验收

## 16.1 联调目标

本手册用于把本地环境从“容器已启动”推进到“关键业务路径可验收”。

本地默认链路如下：

```text
Browser -> Nginx(:8088) -> Vue UI(:5173) -> Java Backend(:8080) -> AiAssistant(:8081) -> Model Provider / Qdrant / MinIO / Redis / MySQL
```

本轮联调必须确认：

- 工作台、项目、文档、文件、搜索、AI、管理后台可访问。
- 普通用户和管理员权限路径符合预期。
- AI 页面能展示会话元信息、模型 / Provider、引用、索引状态和确认弹层。
- Nginx 网关 `/api` 在后端容器重启后仍能恢复访问。
- 发布前自动化回归脚本可复跑。

## 16.2 前置准备

### 基础工具

- Docker Desktop
- Windows PowerShell 或 PowerShell 7
- Chrome 或 Edge 浏览器
- Node 依赖已在 `erise-ai-ui` 中安装

### 环境文件

首次使用时，在仓库根目录执行：

```powershell
Copy-Item .env.dev.example .env.dev
```

至少检查以下变量：

```text
JWT_SECRET
INTERNAL_API_KEY
MYSQL_*
REDIS_*
MINIO_*
QDRANT_*
MODEL_PROVIDER
DEFAULT_MODEL_CODE
EMBEDDING_PROVIDER_CODE
ADMIN_USERNAME
ADMIN_PASSWORD
NGINX_HTTP_PORT
```

如果要验证真实联网搜索，还需要：

```text
WEB_SEARCH_PROVIDER=tavily
TAVILY_API_KEY=<your-key>
```

如果要验证真实 OpenAI / DeepSeek / LiteLLM / vLLM 生成，需要确保对应 Provider 的 `BASE_URL`、`API_KEY`、`MODEL` 与后台模型配置一致。

## 16.3 启动本地全栈

在仓库根目录执行：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d --build
```

查看容器状态：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml ps
```

停止容器：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

推荐全部核心服务达到以下状态再开始联调：

```text
mysql: healthy
redis: healthy
qdrant: healthy
ollama: healthy
backend: healthy
cloud: healthy
ui: Up
nginx: Up
```

常用访问地址：

| 服务          | 地址                                            | 用途            |
| ------------- | ----------------------------------------------- | --------------- |
| 统一入口      | `http://localhost:8088`                         | 浏览器主入口    |
| 前端直连      | `http://localhost:5173`                         | Vite 直连调试   |
| Java Backend  | `http://localhost:8080/actuator/health`         | 后端健康检查    |
| AiAssistant   | `http://localhost:8081/internal/ai/chat/health` | AI 服务健康检查 |
| MinIO Console | `http://localhost:9001`                         | 对象存储控制台  |
| Qdrant        | `http://localhost:6333`                         | 向量库接口      |

## 16.4 健康检查

### 网关与后端

```powershell
Invoke-RestMethod http://localhost:8088/actuator/health
Invoke-RestMethod http://localhost:8088/api/v1/auth/captcha
Invoke-RestMethod http://localhost:8080/actuator/health
```

通过标准：

- `/actuator/health` 返回 `UP`。
- `/api/v1/auth/captcha` 返回 `code=0`，且 `data.captchaId`、`data.captchaImage` 不为空。

如果 `8080` 正常但 `8088/api` 返回 502，优先重启 Nginx：

```powershell
docker restart erise-ai-nginx-dev
```

当前 Nginx 配置已使用 Docker DNS 动态解析 `backend`，后端容器重启后不应长期卡在旧 IP。

### AI 服务

```powershell
Invoke-RestMethod http://localhost:8081/internal/ai/chat/health
```

通过标准：

- 返回 `service=erise-ai-chat-service`
- `status=UP`
- `database=UP`
- `redis=UP`

### 日志观察

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f backend
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f cloud
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f nginx
```

## 16.5 登录联调

默认管理员账号来自 `.env.dev`：

```text
ADMIN_USERNAME=admin
ADMIN_PASSWORD=Admin123!
```

浏览器路径：

1. 打开 `http://localhost:8088/login`
2. 输入管理员账号密码
3. 输入验证码
4. 登录后应进入 `/admin`

普通用户可通过登录页注册，或使用自动化验收脚本创建临时普通用户。

### PowerShell 自动登录取 Token

```powershell
$captcha = Invoke-RestMethod http://localhost:8088/api/v1/auth/captcha
$svg = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($captcha.data.captchaImage -split ',')[1]))
$code = [regex]::Match($svg, '>([A-Z0-9]{4})<').Groups[1].Value

$login = Invoke-RestMethod http://localhost:8088/api/v1/auth/login `
  -Method Post `
  -ContentType 'application/json' `
  -Body (@{
    username = 'admin'
    password = 'Admin123!'
    captchaId = $captcha.data.captchaId
    captchaCode = $code
  } | ConvertTo-Json)

$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
```

## 16.6 前端关键路径手工验收

### 普通用户路径

1. 登录普通用户。
2. 进入 `/workspace`，检查最近项目、最近文件/文档、AI 快捷入口。
3. 进入 `/projects`，创建或打开项目。
4. 进入项目详情，检查“项目 AI”入口。
5. 进入 `/documents`，创建、预览、编辑、删除一篇文档。
6. 进入 `/files`，上传一个 `.txt` 或 `.md` 文件，观察解析 / 索引状态。
7. 进入 `/search`，按项目过滤搜索文档或文件标题。
8. 进入 `/ai` 或 `/projects/:id/ai`，检查会话元信息、模型 / Provider、索引状态、引用卡片、确认弹层。

### 管理员路径

1. 登录管理员。
2. 进入 `/ai`，确认左侧导航出现“管理后台”入口。
3. 进入 `/admin`，确认仪表盘可访问。
4. 进入 `/admin/ai/models`，检查模型配置。
5. 进入 `/admin/ai/index-tasks`，检查索引任务。
6. 进入 `/admin/logs`，检查审计日志。
7. 进入 `/admin/acceptance`，检查前端闭环验收页。

## 16.7 自动化回归

在 `erise-ai-ui` 目录执行：

```powershell
npm run build
npm run e2e -- tests/e2e/acceptance.spec.ts
```

通过标准：

```text
2 passed
```

当前 Playwright 默认配置：

```text
PLAYWRIGHT_BASE_URL=http://127.0.0.1:8088
PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:8088/api
PLAYWRIGHT_BROWSER_CHANNEL=chrome
```

如果需要切换目标环境：

```powershell
$env:PLAYWRIGHT_BASE_URL='http://localhost:8088'
$env:PLAYWRIGHT_API_BASE_URL='http://localhost:8088/api'
npm run e2e -- tests/e2e/acceptance.spec.ts
```

说明：

- 自动化脚本会真实创建普通用户、项目、文档、文件，并验证工作台、项目、搜索和管理员路径。
- AI 会话详情使用稳定 fixture，目的是验证前端展示闭环，不把回归稳定性绑定到模型生成耗时。
- 真实 Provider 生成能力需要按 `16.9` 单独冒烟。

## 16.8 文件、索引与搜索联调

推荐最小链路：

1. 创建项目。
2. 上传 `.txt` 文件。
3. 进入项目文件列表，观察 `KnowledgeSyncStatus`。
4. 等待状态从 `PENDING / PROCESSING` 推进到 `READY / SUCCESS` 或失败态。
5. 搜索文件名，确认 `/search` 能返回结果。
6. 若失败，点击“重新解析”。

重点观察：

- 文件上传状态是否为 `READY`。
- `parseStatus` 是否推进。
- `indexStatus` 是否推进。
- 失败时是否显示 `parseErrorMessage`。
- 管理后台 `/admin/ai/index-tasks` 是否有对应任务。

## 16.9 AI/RAG 真实生成冒烟

### General 模式

```powershell
$general = Invoke-RestMethod http://localhost:8088/api/v1/ai/chat `
  -Method Post `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body (@{
    question = '请用一句话说明当前系统的用途'
    mode = 'GENERAL'
    webSearchEnabled = $false
    topK = 5
    similarityThreshold = 0.65
  } | ConvertTo-Json)
```

通过标准：

- 返回 `sessionId`
- 返回 `answer`
- 返回 `modelCode`、`providerCode` 或在管理后台请求日志中可查

### Scoped 模式

先准备一个文档或文件作为附件，再调用：

```powershell
$scoped = Invoke-RestMethod http://localhost:8088/api/v1/ai/chat `
  -Method Post `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body (@{
    projectId = 1
    question = '请根据附件总结要点'
    mode = 'SCOPED'
    webSearchEnabled = $false
    attachments = @(
      @{
        attachmentType = 'DOCUMENT'
        sourceId = 1
        projectId = 1
        title = '本地联调文档'
      }
    )
  } | ConvertTo-Json -Depth 8)
```

通过标准：

- 不越界引用无关项目数据。
- 引用来源能在 AI 页面以卡片形式展示。
- 点击引用能跳转到文档、文件或内容详情。

### 临时文件

浏览器路径更直观：

1. 打开 `/ai`
2. 新建或进入会话
3. 点击输入框左侧 `+`
4. 上传临时文件
5. 观察索引状态卡片与输入框附件托盘
6. 提问时确认临时文件被纳入上下文

## 16.10 管理后台联调重点

### 模型配置

路径：`/admin/ai/models`

检查项：

- 默认模型只有一个。
- 当前默认模型 Provider 与 `.env.dev` 中 `MODEL_PROVIDER`、`DEFAULT_MODEL_CODE` 一致。
- `supportStream` 与真实 Provider 能力一致。
- `maxContextTokens` 有合理值。

### 索引任务

路径：`/admin/ai/index-tasks`

检查项：

- 文件上传后能看到任务记录。
- 失败任务能展示错误原因。
- 可重试任务显示重试入口。

### 请求日志

路径：`/admin/ai/request-logs`

检查项：

- AI 请求有记录。
- 失败请求能看到错误原因。
- Provider、模型、耗时可追踪。

## 16.11 n8n / MCP 外围联调

本轮前端闭环不要求 n8n 接管核心问答。n8n 只验证外围工作流：

- 审批通知
- 巡检告警
- 周报分发
- 通知扇出

MCP 本地联调重点：

- 普通用户只能看到只读工具。
- 普通用户不能调用管理员工具。
- 工具调用失败应被审计记录。

相关目录：

```text
deploy/n8n/
deploy/mcp/
docs/rebuild/10_mcp_readonly.md
docs/rebuild/11_mcp_client_examples.md
docs/rebuild/12_n8n_workflows.md
```

## 16.12 常见问题

### 8088 页面能打开，但 `/api` 返回 502

检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8088/api/v1/auth/captcha
docker logs --tail 80 erise-ai-nginx-dev
```

处理：

```powershell
docker restart erise-ai-nginx-dev
```

如果仍失败，检查 `deploy/nginx/default.dev.conf` 是否包含：

```nginx
resolver 127.0.0.11 valid=10s ipv6=off;
set $backend_upstream backend:8080;
```

### AI 回复超过 120 秒

优先检查：

- 当前 Provider 是否可用。
- Ollama 模型是否已拉取完成。
- LiteLLM / vLLM 网关是否健康。
- Qdrant 与 embedding 是否正常。

处理建议：

- 前端闭环回归先跑 `npm run e2e -- tests/e2e/acceptance.spec.ts`。
- 真实生成单独执行 `16.9` 冒烟，不阻塞 UI 闭环验收。

### 文件索引一直 PENDING

检查：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f backend
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f cloud
```

重点看：

- MinIO 对象是否存在。
- Qdrant 是否健康。
- embedding Provider 是否可用。
- 后台 `/admin/ai/index-tasks` 是否记录失败原因。

### Playwright 找不到浏览器

当前配置默认复用本机 Chrome：

```text
PLAYWRIGHT_BROWSER_CHANNEL=chrome
```

如果本机没有 Chrome：

```powershell
npx playwright install chromium
$env:PLAYWRIGHT_BROWSER_CHANNEL='chromium'
npm run e2e -- tests/e2e/acceptance.spec.ts
```

### 登录验证码错误

处理：

- 重新刷新验证码。
- PowerShell 自动登录时确认读取的是 `captcha.data.captchaImage` 与 `captcha.data.captchaId`。
- 不要重复使用已经提交过的验证码。

## 16.13 停止与清理

停止服务但保留数据：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

停止服务并清空卷：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml down -v
```

只重启某个服务：

```powershell
docker restart erise-ai-nginx-dev
docker restart erise-ai-backend-dev
docker restart erise-ai-chat-service-dev
```

## 16.14 联调通过标准

本地联调可以判定通过的最低标准：

- `docker compose ps` 中核心服务均为 `Up`，关键服务健康。
- `http://localhost:8088/api/v1/auth/captcha` 返回 200。
- `http://localhost:8088/actuator/health` 返回 `UP`。
- `npm run build` 通过。
- `npm run e2e -- tests/e2e/acceptance.spec.ts` 通过。
- 管理员可进入 `/admin/acceptance`。
- 普通用户不能进入 `/admin`。
- 至少一次真实 AI 生成冒烟完成，或已明确记录 Provider 不可用原因。
