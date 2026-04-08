# Erise-AI 本地运维手册

本文面向当前仓库的本地 Docker 开发环境，默认运行方式为：

`UI -> Nginx -> Java Backend -> Python AiAssistant -> Model Provider`

## 1. 环境准备

推荐使用以下基础环境：

- Docker Desktop
- PowerShell 7 或 Windows PowerShell
- 可用的模型密钥
- 可用的 Tavily 密钥

首次启动前，在仓库根目录准备 `.env.dev`：

```powershell
Copy-Item .env.dev.example .env.dev
```

至少确认以下变量已经正确填写：

- `INTERNAL_API_KEY`
- `JWT_SECRET`
- `OPENAI_API_KEY` 或 `DEEPSEEK_API_KEY`
- `DEFAULT_MODEL_CODE`
- `QDRANT_API_KEY`
- `WEB_SEARCH_PROVIDER=tavily`
- `TAVILY_API_KEY`

注意：

- `WEB_SEARCH_PROVIDER` 必须填写 provider 名称，目前支持 `tavily` 或 `duckduckgo`
- 不要把 API Key 填到 `WEB_SEARCH_PROVIDER`
- 如果独立启动 `AiAssistant`，它读取的是 `AiAssistant/.env` 和进程环境变量，不会自动读取 `AiAssistant/.env.dev`

## 2. 启动与停止

在仓库根目录启动全部开发服务：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml up -d --build
```

查看服务状态：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml ps
```

停止服务：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

如果需要连同卷一起清理：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml down -v
```

## 3. 访问地址

- 统一入口：`http://localhost:8088`
- 前端直连：`http://localhost:5173`
- Backend 健康检查：`http://localhost:8080/actuator/health`
- AiAssistant 健康检查：`http://localhost:8081/internal/ai/chat/health`
- MinIO Console：`http://localhost:9001`
- Qdrant HTTP：`http://localhost:6333`

## 4. 健康检查

检查 Docker 服务状态：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml ps
```

查看关键服务日志：

```powershell
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f cloud
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f backend
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f ui
```

常用 HTTP 检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8081/internal/ai/chat/health -Headers @{
  'X-Internal-Service-Token' = (Get-Content .env.dev | Select-String '^INTERNAL_API_KEY=').ToString().Split('=')[1]
  'X-User-Id' = '1'
  'X-Org-Id' = '0'
}
```

## 5. 联网搜索联调流程

### 5.1 登录并拿到 token

下面的 PowerShell 脚本会自动获取验证码、解析验证码文字并完成登录：

```powershell
$captcha = Invoke-RestMethod http://localhost:8088/api/v1/auth/captcha
$svg = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($captcha.captchaImage -split ',')[1]))
$code = [regex]::Match($svg, '>([A-Z0-9]{4})<').Groups[1].Value
$login = Invoke-RestMethod http://localhost:8088/api/v1/auth/login -Method Post -ContentType 'application/json' -Body (@{
  username = 'admin'
  password = 'Admin123!'
  captchaId = $captcha.captchaId
  captchaCode = $code
} | ConvertTo-Json)
```

### 5.2 设置当前用户默认检索参数

```powershell
$headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
Invoke-RestMethod http://localhost:8088/api/v1/ai/settings/retrieval -Method Put -Headers $headers -ContentType 'application/json' -Body (@{
  similarityThreshold = 0.95
  topK = 5
  webSearchEnabledDefault = $true
} | ConvertTo-Json)
```

说明：

- `GENERAL` 模式下，当私有知识不足以达到阈值时，会继续走 Tavily 搜索
- `SCOPED` 模式下，即使默认开启联网，也不会触发联网搜索

### 5.3 验证 GENERAL 模式是否真实命中 Tavily

```powershell
$general = Invoke-RestMethod http://localhost:8088/api/v1/ai/chat -Method Post -Headers $headers -ContentType 'application/json' -Body (@{
  question = 'What is Tavily and what does it provide for AI search?'
  mode = 'GENERAL'
  webSearchEnabled = $true
  similarityThreshold = 0.95
  topK = 5
} | ConvertTo-Json)
```

期望结果：

- `usedTools` 包含 `web_search`
- `citations` 中至少存在一个 `sourceType=WEB`
- `messageStatus` 为成功状态

### 5.4 验证 SCOPED 模式不会越界联网

```powershell
$scoped = Invoke-RestMethod http://localhost:8088/api/v1/ai/chat -Method Post -Headers $headers -ContentType 'application/json' -Body (@{
  question = 'Tell me the latest public news about Tavily'
  mode = 'SCOPED'
  webSearchEnabled = $true
  similarityThreshold = 0.95
  topK = 5
  attachments = @()
} | ConvertTo-Json)
```

期望结果：

- 不出现 `WEB` 类型引用
- 回复内容为“范围内依据不足”一类的受限回答

## 6. AI 页面可视化验证

推荐从浏览器统一入口验证：

1. 打开 `http://localhost:8088/login`
2. 使用默认管理员账号登录
3. 打开 `http://localhost:8088/ai`
4. 保持当前对话为空白，不选择项目、不附加文件、不上传临时文件
5. 提问一个私有知识库明显未覆盖的问题
6. 确认消息中出现 `WEB` 引用卡片，点击后可打开外部链接
7. 再切换到带项目或附件的上下文，确认此时进入 `SCOPED` 行为，不会展示 `WEB` 引用

重点检查项：

- 模型列表能正常加载
- 消息流与加载态正常
- `WEB` 引用展示文案、链接跳转、错误提示正常
- 不发生页面白屏、401 重定向循环、消息重复写入

## 7. 常见故障排查

### 7.1 `WEB_SEARCH_PROVIDER` 配错

现象：

- `cloud` 服务启动失败，或日志提示 provider 不支持

处理：

- 把 `WEB_SEARCH_PROVIDER` 改成 `tavily`
- 不要把 Tavily API Key 填到这个变量

### 7.2 `TAVILY_API_KEY` 缺失

现象：

- `WEB_SEARCH_PROVIDER=tavily` 时，启动阶段或调用阶段报配置错误

处理：

- 在 `.env.dev` 中补齐 `TAVILY_API_KEY`
- 重启 `cloud` 容器

### 7.3 页面能打开但 AI 回复失败

优先排查：

- `backend` 与 `cloud` 是否都健康
- 模型密钥是否可用
- `DEFAULT_MODEL_CODE` 是否与后端模型配置一致
- `cloud` 日志中是否存在 provider 或 embedding 错误

### 7.4 Qdrant 不可用

现象：

- 临时文件或知识入库失败
- 聊天无法返回私有知识引用

处理：

- 检查 `qdrant` 容器状态
- 检查 `QDRANT_API_KEY` 是否和 Compose 注入一致
- 必要时执行 `docker compose ... down -v` 后重建

## 8. 生产前注意事项

- 开发环境允许 embedding 的非生产 fallback，仅用于联调便利，不应用于生产
- 已在对话中暴露过的 API Key 建议立即轮换
- 示例文件只能保留占位值，不要提交真实密钥
- 生产部署前需要补齐日志留存、审计、备份与外部依赖监控
