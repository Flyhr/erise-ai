# Erise MCP 客户端示例

此目录提供两种访问方式下可直接复制使用的 MCP 客户端示例：

- `clients/erise-mcp-proxy.json`：推荐用于已经使用 Erise JWT 的前端或桌面客户端
- `clients/erise-mcp-direct.json`：直接访问 AiAssistant MCP 端点
- `clients/erise-mcp-request-examples.json`：用于 smoke check 的 JSON-RPC 请求体示例

## 推荐地址

- 代理方式：`http://localhost:8080/api/v1/ai/mcp`
- 直连方式：`http://localhost:8081/mcp`

## 必需请求头

无论使用哪种路径，都需要携带 Erise access token：

```http
Authorization: Bearer <ERISE_ACCESS_TOKEN>
```

## 前端用法

前端可以直接复用现有 helper 调用代理端点，相关代码位于：

- `erise-ai-ui/src/api/ai.ts`

示例：

```ts
import { proxyMcp } from '@/api/ai'

const response = await proxyMcp({
  jsonrpc: '2.0',
  id: 'projects-1',
  method: 'tools/call',
  params: {
    name: 'projects.list',
    arguments: {
      pageNum: 1,
      pageSize: 10,
    },
  },
})
```
