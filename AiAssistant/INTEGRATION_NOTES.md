# 集成改动指南

本次未修改主项目文件，需在 `EriseAi` 主仓做如下接入：

1. **部署与启动**
   - 在根目录新增服务启动脚本或 PM2/systemd 配置，运行命令：`cd AiAssistant && uvicorn src.main:app --host 0.0.0.0 --port 8090`。
   - 确保主项目的环境变量里配置 `OPENAI_API_KEY` 与可选的 `ASSISTANT_*` 前缀变量。

2. **反向代理 / API 网关**
   - 如果前端/后端需要统一域名，建议在 `erise-ai-backend` 网关层新增路由，将 `/assistant/*` 代理到 `localhost:8090`。
   - 若使用 Nginx，在站点配置中加入：
     ```nginx
     location /assistant/ {
         proxy_pass http://127.0.0.1:8090/;
     }
     ```

3. **前端调用**
   - 在 `erise-ai-ui` 中添加 API 封装（例如 `services/assistant.ts`），暴露：
     - `POST /assistant/chat` body: `{ message, history?, use_search? }`。
     - `GET /assistant/analyze?focus=backend`。
   - UI 里可提供“项目解读”与“知识问答”两个 Tab，复用现有聊天组件。

4. **权限与限流**
   - 若主项目已有登录鉴权，建议在网关层为 `/assistant/*` 添加同样的 auth 中间件或 JWT 校验。
   - 如需保护 OpenAI 额度，可在网关层做简单的 QPS 限制（如 10 rpm）。

5. **日志与监控**
   - 在主项目统一的日志系统中，为 Assistant 服务增加访问日志采集；也可用 `uvicorn --access-log` 直接输出。

6. **可选优化**
   - 将 `src/services/search.py` 替换为公司内部搜索或向量库调用，保持同样的接口签名 `web_search(query, max_results)` 即可。
   - 若需缓存项目摘要，可在网关层增加缓存层，或在 `summarize_project` 中持久化结果到 Redis。
