# 16. 本地联调手册

- 版本：前端闭环与 AI / RAG 联调批次
- 日期：2026-04-20
- 适用范围：本地 Docker 开发环境、前端验收、AI / RAG / 索引联调

## 16.1 联调目标

本手册用于把本地环境从“容器已启动”推进到“关键业务路径可验收”。

默认联调链路：

```text
Browser -> Nginx(:8088) -> Vue UI(:5173) -> Java Backend(:8080) -> AiAssistant(:8081) -> Qdrant / MinIO / Redis / MySQL / Ollama
```

当前 AI / RAG 重点确认项：

- AI 检索已经使用 Qdrant dense+sparse 一体化索引
- Java 内部 `/internal/v1/knowledge/retrieve` 不再服务 AI 主检索
- 文件索引任务可以从 `PENDING / PROCESSING` 推进到 `SUCCESS`
- Qdrant collection 可以按当前实现自动重建为 hybrid 结构

## 16.2 启动环境

在仓库根目录执行：

```powershell
docker compose --env-file .env -f docker-compose.yml up -d
```

查看状态：

```powershell
docker compose --env-file .env -f docker-compose.yml ps
```

建议至少确认以下服务正常：

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

## 16.3 常用地址

| 服务 | 地址 | 用途 |
| --- | --- | --- |
| 统一入口 | `http://localhost:8088` | 浏览器主入口 |
| 前端直连 | `http://localhost:5173` | Vite 调试 |
| Java Backend | `http://localhost:8080/actuator/health` | 后端健康检查 |
| AiAssistant | `http://localhost:8081/internal/ai/chat/health` | AI 服务健康检查 |
| MinIO Console | `http://localhost:9001` | 对象存储控制台 |
| Qdrant | `http://localhost:6333` | 向量库接口 |

## 16.4 健康检查

### 后端与网关

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8088/actuator/health
Invoke-RestMethod http://localhost:8088/api/v1/auth/captcha
```

### AI 服务

```powershell
Invoke-RestMethod http://localhost:8081/internal/ai/chat/health
```

通过标准：

- `service = erise-ai-chat-service`
- `status = UP`
- `database = UP`
- `redis = UP`

## 16.5 登录获取管理员 Token

默认管理员账号来自 `.env`：

```text
ADMIN_USERNAME=admin
ADMIN_PASSWORD=Admin123!
```

PowerShell 登录脚本：

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

## 16.6 文件与索引联调

推荐最小闭环：

1. 打开 `/files`
2. 上传一个可索引文件，例如 `.txt` / `.md` / `.pdf` / `.docx`
3. 观察文件 `parseStatus` 与 `indexStatus`
4. 进入 `/admin/ai/index-tasks` 查看任务推进

重点观察：

- `parseStatus`
- `indexStatus`
- `parseErrorMessage`
- `/admin/ai/index-tasks` 中是否存在对应任务

## 16.7 Hybrid 索引重建

如果当前环境中的历史索引都是测试数据，可直接执行重建。

完整说明参考：

- [17_rag_hybrid_index_migration.md](./17_rag_hybrid_index_migration.md)

最小操作流程：

1. 启动环境
2. 确认 `backend / cloud / qdrant` 健康
3. 登录拿到管理员 token
4. 删除旧测试 collection
5. 重新触发现有资产进入索引链
6. 轮询直到 `indexStatus=SUCCESS`

### 删除旧 collection

```powershell
Invoke-RestMethod -Method Delete `
  -Headers @{ 'api-key' = 'dev-qdrant-key' } `
  http://localhost:6333/collections/kb_chunks
```

### 查看 collection 结构

```powershell
Invoke-RestMethod -Headers @{ 'api-key' = 'dev-qdrant-key' } `
  http://localhost:6333/collections/kb_chunks | ConvertTo-Json -Depth 12
```

### 查看点数

```powershell
Invoke-RestMethod -Headers @{ 'api-key' = 'dev-qdrant-key' } `
  http://localhost:6333/collections/kb_chunks/points/count `
  -Method Post -ContentType 'application/json' -Body '{}' | ConvertTo-Json -Depth 8
```

## 16.8 AI / RAG 冒烟

### GENERAL 模式

```powershell
Invoke-RestMethod http://localhost:8088/api/v1/ai/chat `
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

### SCOPED 模式

先准备附件或项目资料后再调用：

```powershell
Invoke-RestMethod http://localhost:8088/api/v1/ai/chat `
  -Method Post `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body (@{
    projectId = 1
    question = '请根据当前项目资料总结要点'
    mode = 'SCOPED'
    webSearchEnabled = $false
  } | ConvertTo-Json -Depth 8)
```

## 16.9 常见问题

### `/api` 返回 502

优先检查：

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
docker restart erise-ai-nginx
```

### 索引长时间停在 `PROCESSING`

优先检查：

```powershell
docker compose --env-file .env -f docker-compose.yml logs -f backend
docker compose --env-file .env -f docker-compose.yml logs -f cloud
```

重点查看：

- Qdrant 是否健康
- embedding provider 是否可用
- 是否存在数据库锁等待超时
- `/admin/ai/index-tasks` 是否出现失败记录

### 旧 dense-only collection 仍然存在

当前实现会在访问时自动识别旧 schema 并按 hybrid 结构重建。

如果你想立即清空测试数据，也可以手工删除后再触发资产重索引。

## 16.10 停止环境

停止但保留数据卷：

```powershell
docker compose --env-file .env -f docker-compose.yml down
```

停止并清空卷：

```powershell
docker compose --env-file .env -f docker-compose.yml down -v
```
