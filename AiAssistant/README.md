AiAssistant
============

Standalone AI 助手子模块，使用 Python + FastAPI + OpenAI 官方 SDK。功能：
- 项目分析：从 `EriseAi` 根目录采样关键文件，生成模块化总结，帮助用户快速理解项目。
- 知识问答 / 搜索：面向用户提问，支持上下文对话，必要时可调用 Web 搜索适配器。

快速开始
--------
1. 安装依赖（建议虚拟环境）：
   ```bash
   pip install -r requirements.txt
   ```
2. 配置环境变量（可放 `.env`，或在启动前导出）：
   - `OPENAI_API_KEY`：必填，用于大模型调用。
   - `ASSISTANT_PROJECT_ROOT`：默认 `D:/EriseAi`，用于分析时的项目根路径。
   - `ASSISTANT_MAX_FILES`：可选，分析时采样的最大文件数，默认 40。
3. 启动服务：
   ```bash
   uvicorn src.main:app --reload --port 8090
   ```
4. 示例调用：
   ```bash
   curl -X POST http://localhost:8090/chat \
     -H "Content-Type: application/json" \
     -d '{"message":"总结前后端技术栈","history":[]}'
   ```

目录结构
--------
- `src/config.py`：环境配置与默认值。
- `src/services/llm_client.py`：OpenAI Chat Completions 封装。
- `src/services/search.py`：DuckDuckGo Web 搜索适配器（可替换）。
- `src/services/project_index.py`：项目文件采样、轻量解析。
- `src/services/summarizer.py`：项目总结与问答逻辑。
- `src/routers/`：API 路由（`chat`、`analyze`）。
- `tests/`：占位测试。
- `INTEGRATION_NOTES.md`：说明在主项目需要的接入改动（本地不直接改主项目）。

设计要点
--------
- 完全放置在 `AiAssistant/` 下，不改动主项目文件。
- 通过配置项 decouple，与主项目耦合点仅为“读取项目文件”和“对外 API 调用”。
- 搜索适配器可替换为公司内搜索或向量库，接口保持稳定。

后续可选
--------
- 接入向量数据库（如 Qdrant / Milvus）做长程记忆。
- 将分析结果写入缓存或静态报告供前端消费。
- 增加身份鉴权（API key / JWT）。
