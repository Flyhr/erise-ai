# Erise-AI 前端页面与路由设计文档（V1）

## 1. 技术基线
- Node.js 24 LTS
- Vue 3 + Vite
- Vue Router
- Axios
- Element Plus
- Pinia
- TypeScript
- Tiptap / pdf.js / Monaco

## 2. 信息架构
### 用户端
- 工作台
- 项目
- 搜索
- AI 助手
- 最近历史
- 个人设置

### 管理端
- 仪表盘
- 用户管理
- 项目审计
- 文件审计
- 任务管理
- AI 配置
- 系统配置
- 操作日志

## 3. 路由建议
### 公共路由
- `/login`
- `/404`
- `/`（重定向）

### 用户端路由
- `/workspace`
- `/projects`
- `/projects/:id`
- `/projects/:id/files`
- `/projects/:id/documents`
- `/projects/:id/ai`
- `/files/:id`
- `/documents/:id`
- `/documents/:id/edit`
- `/search`
- `/ai`
- `/history`
- `/settings/profile`

### 管理端路由
- `/admin`
- `/admin/users`
- `/admin/projects`
- `/admin/files`
- `/admin/tasks`
- `/admin/ai-models`
- `/admin/system-config`
- `/admin/audit-logs`

## 4. 页面重点
### 登录页
- 账号/密码/验证码
- 登录按钮 loading
- 回车登录
- 验证码刷新

### 工作台首页
- 最近项目
- 最近文件
- 最近文档
- 最近 AI 会话
- 快捷入口

### 项目详情
- 概览 / 文件 / 文档 / 标签 / AI

### 文件页
- 上传
- 预览
- 下载
- 标签筛选
- 解析状态

### 文档编辑页
- 富文本编辑
- 图片插入
- 代码块
- PDF/附件引用
- 自动保存状态
- AI 辅助入口

### AI 助手页
- 会话列表
- 消息流
- 引用展示
- 工具调用结果展示

## 5. 状态管理建议
- authStore
- userStore
- projectStore
- appStore
- aiStore

## 6. 路由权限
- 未登录跳转登录页
- `/admin/**` 仅管理员可访问
- 前端权限仅用于体验，真正安全边界在后端
