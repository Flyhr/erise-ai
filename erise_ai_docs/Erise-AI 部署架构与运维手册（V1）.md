# Erise-AI 部署架构与运维手册（V1）

## 1. 部署结论
Erise-AI 推荐：
- 本地开发：前端可本机跑，后端和基础组件优先 Docker Compose
- 联调/测试：全量 Docker Compose
- 轻量生产：Docker Compose + Nginx + 持久化卷 + 备份策略
- 中后期：迁移 Kubernetes

## 2. 架构
```text
Browser
 -> Nginx
   -> erise-ai-ui
   -> erise-ai-backend
   -> erise-ai-cloud
      -> OpenAI / Embedding Provider

Backend / AI Cloud
 -> MySQL
 -> Redis
 -> MinIO
 -> Vector Store
```

## 3. 服务清单
- nginx
- erise-ai-ui
- erise-ai-backend
- erise-ai-cloud
- mysql
- redis
- minio
- vector-store（可选）

## 4. 目录建议
```text
erise-ai/
├─ erise-ai-ui/
├─ erise-ai-backend/
├─ erise-ai-cloud/
├─ deploy/
│  ├─ docker/
│  ├─ scripts/
│  └─ env/
```

## 5. 环境变量
### 主体后端
- SPRING_PROFILES_ACTIVE
- MYSQL_HOST / MYSQL_PORT / MYSQL_DB / MYSQL_USER / MYSQL_PASSWORD
- REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
- MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY
- JWT_SECRET

### AI 微服务
- OPENAI_API_KEY
- OPENAI_BASE_URL
- CHAT_MODEL
- EMBEDDING_MODEL
- VECTOR_STORE_TYPE
- BACKEND_INTERNAL_BASE_URL

### 前端
- VITE_API_BASE_URL
- VITE_WS_BASE_URL
- VITE_APP_TITLE

## 6. 发布流程
1. 构建前端产物与后端 jar
2. 构建 Docker 镜像
3. 推送镜像仓库
4. docker compose pull && docker compose up -d
5. 执行数据库迁移
6. 冒烟测试

## 7. 健康检查
- 后端：`/actuator/health`
- AI 微服务：`/actuator/health`
- Nginx：首页/API/WebSocket/SSE 验证

## 8. 备份与恢复
- MySQL 每日全量备份 + binlog（可选）
- MinIO 周期对象备份
- 配置与 compose 文件备份
- 至少在测试环境做恢复演练

## 9. 运维重点
- 日志分类：应用、审计、任务、AI 调用、Nginx
- 监控指标：API 耗时、错误率、上传成功率、任务失败率、AI 调用耗时、磁盘与连接数
- 安全：仅开放 80/443，数据库/Redis/MinIO 不直接暴露公网，密钥不入 Git

## 10. 最终建议
对于 Erise-AI 这种“前端 + 主体后端 + AI 微服务 + MySQL + Redis + 对象存储 + 向量检索”的系统，V1 最合适的部署方式就是 Docker / Docker Compose。
