# Erise MCP Client Samples

This directory contains ready-to-copy MCP client samples for two access patterns:

- `clients/erise-mcp-proxy.json`: recommended for frontend or desktop clients that already use Erise JWT tokens.
- `clients/erise-mcp-direct.json`: direct access to the AiAssistant MCP endpoint.
- `clients/erise-mcp-request-examples.json`: JSON-RPC request bodies for smoke checks.

## Recommended URLs

- Proxy: `http://localhost:8080/api/v1/ai/mcp`
- Direct: `http://localhost:8081/mcp`

## Required Header

Both paths require an Erise access token:

```http
Authorization: Bearer <ERISE_ACCESS_TOKEN>
```

## Frontend Usage

The frontend can call the proxy directly with the existing helper in:

- `erise-ai-ui/src/api/ai.ts`

Example:

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
