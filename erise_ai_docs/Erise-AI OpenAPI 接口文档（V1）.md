# Erise-AI OpenAPI 接口文档（V1）

## 1. 基础规范
- 接口前缀：`/api/v1`
- 鉴权方式：`Authorization: Bearer <token>`
- 统一响应：
```json
{"code":0,"message":"success","data":{}}
```
- 分页响应：records/pageNum/pageSize/total/totalPages

## 2. 错误码分段
- 400xxx 参数错误
- 401xxx 认证失败
- 403xxx 权限不足
- 404xxx 资源不存在
- 409xxx 状态冲突
- 500xxx 系统异常
- 510xxx 文件异常
- 520xxx AI 异常
- 530xxx 搜索异常

## 3. 核心接口
### 认证
- POST `/api/v1/auth/register`
- POST `/api/v1/auth/login`
- POST `/api/v1/auth/refresh`
- POST `/api/v1/auth/logout`
- GET  `/api/v1/auth/captcha`

### 用户
- GET `/api/v1/users/me`
- PUT `/api/v1/users/me`
- PUT `/api/v1/users/password`

### 项目
- GET `/api/v1/projects`
- POST `/api/v1/projects`
- GET `/api/v1/projects/{id}`
- PUT `/api/v1/projects/{id}`
- POST `/api/v1/projects/{id}/archive`
- DELETE `/api/v1/projects/{id}`

### 文件
- POST `/api/v1/files/init-upload`
- POST `/api/v1/files/upload`
- POST `/api/v1/files/complete-upload`
- GET `/api/v1/files/{id}`
- GET `/api/v1/files/{id}/preview`
- GET `/api/v1/files/{id}/download`
- DELETE `/api/v1/files/{id}`
- POST `/api/v1/files/{id}/tags`

### 文档
- POST `/api/v1/documents`
- GET `/api/v1/documents/{id}`
- PUT `/api/v1/documents/{id}`
- POST `/api/v1/documents/{id}/publish`
- GET `/api/v1/documents/{id}/versions`
- GET `/api/v1/documents/{id}/versions/{versionNo}`
- DELETE `/api/v1/documents/{id}`

### 搜索
- GET `/api/v1/search`
- GET `/api/v1/search/suggest`
- GET `/api/v1/search/history`

### AI
- POST `/api/v1/ai/chat`
- POST `/api/v1/ai/chat/stream`
- GET `/api/v1/ai/sessions`
- GET `/api/v1/ai/sessions/{id}`
- DELETE `/api/v1/ai/sessions/{id}`

### 管理端
- GET `/api/v1/admin/users`
- POST `/api/v1/admin/users/{id}/status`
- GET `/api/v1/admin/files`
- GET `/api/v1/admin/tasks`
- GET `/api/v1/admin/audit-logs`
- GET `/api/v1/admin/ai/models`

## 4. 实施建议
- 使用 springdoc-openapi + Knife4j
- 按模块分组生成接口文档
- Entity 不直出，统一 VO 返回
