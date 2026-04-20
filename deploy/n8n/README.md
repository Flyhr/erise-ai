# n8n 说明

n8n 仍然只作为外围自动化，不进入同步 AI 聊天主链。

## 当前使用方式

如需启用 n8n，请直接使用统一主环境文件 `.env`：

```bash
docker compose -f deploy/n8n/docker-compose.yml --env-file .env up -d
```

## 已废弃内容

- `deploy/n8n/docker-compose.dev.yml`
- 与 `.env.dev` 绑定的开发态 overlay

## 说明

n8n 适合承接以下外部流程：

- 审批通知
- 周报分发
- 告警扇出
- 健康巡检
