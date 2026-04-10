# Erise-AI V1.0

Erise-AI 是一个面向个人的轻量项目知识库系统，核心能力覆盖项目管理、文件与文档编辑、统一检索，支持docx、pdf、md、txt文件上传。包括 AI / RAG检索 能力。

- Ai回答明确区分“指定范围模式”和“通用模式”。指定范围模式禁止越界检索；通用模式必须严格执行“私有知识库 → 联网搜索（开启状态） → 通用知识”三段式降级。
- RAG 体系：MySQL 业务元数据 + MinIO 原始文件 + Qdrant 向量检索

## 运行链路

`erise-ai-ui -> Nginx -> erise-ai-backend -> AiAssistant -> Model Provider`

其中：

- `erise-ai-ui`：Vue 3 + Vite 前端
- `erise-ai-backend`：JAVA + Spring Boot 业务后端与统一网关入口
- `AiAssistant`：Python AI 聊天服务
- `deploy/nginx`：开发态与部署态 Nginx 配置

## 推荐开发方式

开发态统一使用纯 Docker 方式启动，本机无需额外安装 Java、Node 或 Python 运行环境。

首次使用：

1. 复制 `.env.dev.example` 为 `.env.dev`
2. 按需填写 `OPENAI_API_KEY`、`DEEPSEEK_API_KEY`、`INTERNAL_API_KEY` 等配置
3. 如果要启用联网搜索，请填写：
   - `WEB_SEARCH_PROVIDER=tavily`
   - `TAVILY_API_KEY=<your tavily key>`
   - ！！！不要把 API Key 填到 `WEB_SEARCH_PROVIDER`里
4. 在仓库根目录执行：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml up --build
```

停止环境：

```bash
docker compose --env-file .env.dev -f docker-compose.dev.yml down
```

## 开发态访问地址

- Chrome 统一入口：`http://localhost:8088`
- 前端直连调试口：`http://localhost:5173`
- Java Backend 健康检查：`http://localhost:8080/actuator/health`
- Python AI 健康检查：`http://localhost:8081/internal/ai/chat/health`

## 项目主要界面展示

<table>
  <tr>
    <td><img src="./artifacts/login-page.png" alt="登入界面" width="300px" /></td>
    <td><img src="./artifacts/workspace-page.png" alt="首页界面" width="300px" /></td>
    <td><img src="./artifacts/project-page.png" alt="项目界面" width="300px" /></td>
  </tr>
  <tr>
    <td><img src="./artifacts/Konwledge-page.png" alt="知识库界面" width="300px" /></td>
    <td><img src="./artifacts/AiChat-page.png" alt="AI聊天界面" width="300px" /></td>
    <td></td>
  </tr>
</table>

## 热更新说明

目前代码处于开发配置，源码直接挂载到容器内，依赖和构建产物保留在容器卷中：

- 前端：Vite HMR + polling，本地修改 Vue / TS / CSS 后浏览器直接热更新
- Python AI：`uvicorn --reload`，修改 `AiAssistant/src` 后容器内自动 reload
- Java Backend：容器内 watcher + `spring-boot:run` / DevTools 自动重启

说明：

- 前端和 Python 服务可以做到不重新构建镜像即可生效
- Java 代码无法做到“完全不编译就生效”，但编译与重启都发生在容器内部，不需要重新执行 `docker compose up --build`

## 什么时候需要重新 `--build`

仅在以下场景建议重新构建：

- `package.json` / `package-lock.json` 发生变化
- `requirements.txt` 发生变化
- `pom.xml` 或 Maven 依赖发生变化
- `Dockerfile.dev` 或基础镜像发生变化

日常业务代码、样式、配置文件调整通常不需要重新 `--build`

## 生产与其他说明

- 开发态使用：`docker-compose.dev.yml`
- 生产环境仍保留：`docker-compose.yml`
- Nginx 开发态配置文件：`deploy/nginx/default.dev.conf`

## 主要问题说明

### 部分pdf解析失败（已解决）4.9

- 部分pdf解析失败，导致无法被AI助理引用到上下文进行问题回答。后续把 PDF 改成逐页判定、逐页 OCR fallback,并统一 PDF 解析服务，由 Python端 负责

### 优化索引技术

- qdrant向量数据库，需要优化索引技术，考虑混合索引加重排序来提高RAG性能
- 文件的章节识别、切分等处理能力有待提高；文本分块太大导致检索不准，分块太小导致上下文丢失，后续把长文本切成适合模型处理的小块。

### 优化前端界面交互

- 前端界面需要优化
