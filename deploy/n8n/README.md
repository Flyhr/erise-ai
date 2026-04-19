# n8n Peripheral Workflows

This directory contains the first n8n workflow pack for Erise.

## Start

```bash
docker compose --env-file .env.dev -f deploy/n8n/docker-compose.yml up -d
```

## Workflow Files

- `approval-pending.json`
- `approval-applied.json`
- `approval-rejected.json`
- `approval-failed.json`
- `notification-fanout.json`
- `health-inspection-alert.json`
- `weekly-report-distribution.json`

## Scope Boundary

n8n stays outside the synchronous AI chat/completions path.

It is only used for peripheral automation:

- approval flow notifications
- generic notification fan-out
- scheduled inspection
- failure alerts
- weekly report distribution

## Required Environment

Set these values before importing the workflows:

```env
N8N_ENABLED=true
N8N_WEBHOOK_BASE_URL=http://localhost:5678/webhook
N8N_WEBHOOK_SECRET=change-this-n8n-secret
INTERNAL_API_KEY=please-change-this-internal-api-key-to-a-long-random-value
```

## Smoke Checks

### Approval pending

```bash
curl -X POST http://localhost:5678/webhook/approval-pending ^
  -H "Content-Type: application/json" ^
  -d "{\"requestId\":\"demo-approval-1\",\"approvalId\":12,\"actionCode\":\"document.update_title\",\"userId\":1}"
```

### Generic notification

```bash
curl -X POST http://localhost:5678/webhook/notification-fanout ^
  -H "Content-Type: application/json" ^
  -d "{\"requestId\":\"demo-notify-1\",\"eventType\":\"notification.manual\",\"title\":\"Erise 手动通知\",\"content\":\"这是一条来自 n8n 的通知。\",\"sendToAll\":false,\"userIds\":[1]}"
```

### Weekly report distribution

```bash
curl -X POST http://localhost:5678/webhook/weekly-report-created ^
  -H "Content-Type: application/json" ^
  -d "{\"requestId\":\"demo-weekly-1\",\"projectId\":77,\"userId\":1,\"title\":\"Apollo 周报草稿 2026-04-18\"}"
```

## Traceability

- Outbound events from AiAssistant are recorded in `n8n_event_log`
- Inbound calls from n8n into the backend are recorded in `automation_webhook_log`
- Approval overreach or denied live MCP calls are recorded in `mcp_access_log`
