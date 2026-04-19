# n8n 外围工作流

此目录包含 Erise 当前第一批 n8n 工作流打包内容。

当前集成模式是：基于 webhook 驱动的外围自动化，而不是内嵌式工作流引擎。

当前已经具备：

- AiAssistant 会向外发送 webhook 事件，并把每次投递记录落到 `n8n_event_log`
- 审批事件已经可以映射到工作流执行状态
- 事件发送链路已经包含重试、幂等键和 HMAC 签名请求头

当前尚未具备：

- AiAssistant 内部没有工作流运行时
- 没有基于回调的工作流状态回写接口
- 聊天服务内部没有分布式补偿 / 编排引擎

## 启动方式

```bash
docker compose --env-file .env.dev -f deploy/n8n/docker-compose.dev.yml up -d
```

## 工作流文件

- `approval-pending.json`
- `approval-applied.json`
- `approval-rejected.json`
- `approval-failed.json`
- `notification-fanout.json`
- `health-inspection-alert.json`
- `weekly-report-distribution.json`

## 作用边界

n8n 不在同步 AI 聊天 / completions 主链路内。

它当前只用于外围自动化场景：

- 审批流通知
- 通用通知分发
- 定时巡检
- 失败告警
- 周报分发

## 必需环境变量

导入工作流前，请先设置以下环境变量：

```env
N8N_ENABLED=true
N8N_WEBHOOK_BASE_URL=http://localhost:5678/webhook
N8N_WEBHOOK_SECRET=change-this-n8n-secret
N8N_EVENT_MAX_RETRIES=2
N8N_EVENT_RETRY_BACKOFF_SECONDS=1
INTERNAL_API_KEY=please-change-this-internal-api-key-to-a-long-random-value
```

AiAssistant 外发 webhook 时会带上以下请求头：

- `X-Request-Id`
- `X-Idempotency-Key`
- `X-N8N-Webhook-Secret`
- `X-N8N-Signature`
- `X-N8N-Signature-Timestamp`

建议的工作流处理方式：

- 处理 payload 前先校验共享密钥和 HMAC 签名
- 将 `X-Idempotency-Key` 作为重复投递的去重键
- 默认 webhook 投递语义为 at-least-once，而不是 exactly-once

## Smoke Check

### approval pending

```bash
curl -X POST http://localhost:5678/webhook/approval-pending ^
  -H "Content-Type: application/json" ^
  -d "{\"requestId\":\"demo-approval-1\",\"approvalId\":12,\"actionCode\":\"document.update_title\",\"userId\":1}"
```

### 通用通知

```bash
curl -X POST http://localhost:5678/webhook/notification-fanout ^
  -H "Content-Type: application/json" ^
  -d "{\"requestId\":\"demo-notify-1\",\"eventType\":\"notification.manual\",\"title\":\"Erise 手动通知\",\"content\":\"这是一条来自 n8n 的通知。\",\"sendToAll\":false,\"userIds\":[1]}"
```

### 周报分发

```bash
curl -X POST http://localhost:5678/webhook/weekly-report-created ^
  -H "Content-Type: application/json" ^
  -d "{\"requestId\":\"demo-weekly-1\",\"projectId\":77,\"userId\":1,\"title\":\"Apollo 周报草稿 2026-04-18\"}"
```

## 可追踪性

- AiAssistant 外发事件会记录在 `n8n_event_log`
- n8n 回调后端的入站请求会记录在 `automation_webhook_log`
- 审批越权或被拒绝的实时 MCP 调用会记录在 `mcp_access_log`
