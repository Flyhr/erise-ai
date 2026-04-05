# Docker 开发态挂载方案

## 目标

让 Erise-AI 在开发阶段只依赖 Docker 运行，同时保证本地改代码后能够立即影响容器中的服务，不需要反复重新构建镜像。

统一启动命令：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml up --build
```

## 目录挂载策略

### 前端 `ui`

- 源码目录：`./erise-ai-ui -> /workspace`
- 依赖目录：`ui-node-modules -> /workspace/node_modules`
- 运行方式：Vite `dev` 模式

效果：

- 本地修改前端代码后，容器内 Vite 立即感知变更
- 浏览器通过 HMR 自动刷新
- `node_modules` 不落到宿主机，避免本机安装依赖

### Java 后端 `backend`

- 源码目录：`./erise-ai-backend -> /workspace`
- 构建产物：`backend-target -> /workspace/target`
- Maven 缓存：`maven-cache -> /root/.m2`
- 运行方式：容器内 `spring-boot:run` + DevTools / watcher

效果：

- 修改 Java 源码或 `application-dev.yml` 后，容器内自动重新编译并重启
- 不需要重新执行 `docker compose up --build`
- `target` 不写回宿主机，避免本地生成构建垃圾

说明：

- Java 不能做到“源码改完完全不编译就生效”
- 当前实现的上限是“编译和重启都在容器内自动完成”

### Python AI `cloud`

- 源码目录：`./AiAssistant -> /workspace`
- 虚拟环境：`cloud-venv -> /workspace/.venv`
- pip 缓存：`pip-cache -> /root/.cache/pip`
- 运行方式：`uvicorn --reload`

效果：

- 修改 `AiAssistant/src` 后服务自动 reload
- Python 依赖和缓存不污染宿主机目录

## 入口与代理

- Chrome 统一访问入口：`http://localhost:8088`
- 前端直连调试口：`http://localhost:5173`
- 开发态 Nginx 配置：`deploy/nginx/default.dev.conf`

说明：

- `8088` 是推荐入口，适合联调完整链路
- `5173` 仅作为前端直连调试口保留

## 环境文件

开发态统一使用以下文件：

- 根目录：`.env.dev`
- 前端：`erise-ai-ui/.env.dev`
- Python AI：`AiAssistant/.env.dev`
- Java 后端：`application-dev.yml`

建议从这些模板复制：

- `.env.dev.example`
- `erise-ai-ui/.env.dev.example`
- `AiAssistant/.env.dev.example`

## 什么时候需要重新构建

以下场景建议重新执行 `up --build`：

- Node 依赖变化：`package.json` / `package-lock.json`
- Python 依赖变化：`requirements.txt`
- Java 依赖变化：`pom.xml`
- Dockerfile 或基础镜像变化

其余大部分业务代码修改都不需要重新构建镜像。

## 验证命令

检查编排是否正确：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml config
```

启动开发环境：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml up --build
```

查看容器状态：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml ps
```

查看日志：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f ui
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f backend
docker compose --env-file .env.dev -f docker-compose.dev.yml logs -f cloud
```

## 常见问题

### Windows 修改代码后热更新不及时

当前开发态已经开启 polling：

- `CHOKIDAR_USEPOLLING=true`
- `WATCHPACK_POLLING=true`

如果仍然存在延迟，请确认 Docker Desktop 已允许当前磁盘共享。

### 为什么 Java 不能像前端一样完全热替换

因为 JVM 运行中的类变更通常仍需要重新编译并触发类重载或进程重启。当前方案已经把这一步收口到容器内部，避免开发者手动重建镜像或重启整个编排。
