# Erise-AI

Erise-AI 当前默认运行架构已经切换为：

- `erise-ai-ui`：Vue 3 + Vite 前端
- `erise-ai-backend`：Spring Boot 业务后端与 AI 网关
- `AiAssistant`：Python AI 聊天服务

##

- `erise-ai-cloud`：旧 Java AI 服务，不参与运行

## 仓库结构

```text
AiAssistant/        Python AI chat service
erise-ai-backend/  Spring Boot business backend
erise-ai-cloud/    Legacy Java AI service kept for reference
erise-ai-ui/       Vue 3 frontend
deploy/            Nginx and deployment assets
docs/              Integration and deployment notes
docker-compose.yml Local development stack
```

## AI 聊天运行链路

正式链路：

`UI -> /api/v1/ai -> Java Backend -> Python AiAssistant -> Model Provider`

其中：

- Java 负责鉴权、项目权限、公共 API 与 SSE 转发
- Python 负责会话、消息、模型调用、取消生成、项目上下文拼装
- 旧的 `erise-ai-cloud` 不再承接正式功能，只保留源码，

## 快速开始

1. 复制 `.env.example` 为 `.env`
2. 填写 `INTERNAL_API_KEY` 与至少一个模型 provider key
3. 启动整套环境：

```bash
docker compose up --build
```

4. 打开以下地址检查状态：

- Nginx: `http://localhost:8088`
- Backend health: `http://localhost:8080/actuator/health`
- Python AI health: `http://localhost:8081/internal/ai/chat/health`

## `docker compose up --build` 检查清单

1. 运行 `docker compose ps`，确认 `mysql`、`redis`、`cloud` 已健康，`backend`、`ui`、`nginx` 已启动。
2. 运行 `docker compose logs cloud --tail=100`，确认 Python AI 服务没有启动期异常。
3. 运行 `docker compose logs backend --tail=100`，确认 Java 后端没有代理 Python AI 的连接错误。
4. 访问 `http://localhost:8088` 登录系统并进入 AI 页面。
5. 确认 AI 页侧边栏显示的是后端真实返回的模型列表。
6. 发送消息、停止生成、刷新页面，确认完整聊天链路可用。

完整版本见 `docs/AI_CHAT_INTEGRATION.md`。

## Python AI 单独启动

```bash
cd AiAssistant
pip install -r requirements.txt
python scripts/init_db.py
python scripts/smoke_test.py
uvicorn src.app.main:app --host 0.0.0.0 --port 8081 --reload
```

## 关键文档

- `docs/AI_CHAT_INTEGRATION.md`
- `AiAssistant/README.md`
- `AiAssistant/INTEGRATION_NOTES.md`

# 存在问题

1. 项目上传文件中txt文本查看时会乱码，且不能相关doc文件一样在线编辑
2. 上传27.2M大小的的pdf上传失败，140k的pdf文件可以成功:index-DH8bWNw6.js:93 POST http://localhost:8088/api/v1/files/upload 413 (Request Entity Too Large)，且上传失败后，项目列表还是有该文件信息，点击预览没反应，点击详情里面的在线预览提示文件加载失败
3. 项目大小显示内容太多，要修改为大于0.1M的以M单位显示，小于等于0.1M的以KB单位显示，文件类型以文件实际类型为准，例如果是pff，类型里面就是：PDF

4. 添加文档后不会自动关闭添加文档界面，正常流程是，我添加成功文档后，自动跳转到文档浏览界面；
5. 文档的预览功能可以编辑文档的内容，正常是点击预览后，只能查看文件内容，不能编辑，编辑功能是点击“编辑”后才可以的
6. 文档的显示属性多加一个‘创建时间’，文档如果是草稿就背景高亮提示，‘状态’显示‘草稿’，而不是‘
   DRAFT’，如果是‘已发布’就显示‘已发布’，而不是‘PUBLISHED’
7. 将项目文件界面中的‘解析状态’改为文件‘上传时间’，‘索引状态’改为显示文件的‘更新时间’，
