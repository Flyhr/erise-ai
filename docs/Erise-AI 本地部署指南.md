# Erise-AI 本地部署指南

本文档基于当前仓库里的实际实现编写，适用于 Windows 本地开发和本地联调。

## 1. 部署方式选择

推荐优先使用下面两种方式之一：

1. 全量 Docker 模式
   适合想快速跑起来。前端、后端、AI 微服务、MySQL、Redis、MinIO 都走容器。

2. 混合开发模式
   适合本地调试。MySQL、Redis、MinIO 走 Docker，前端和两个 Java 服务在本机启动。

不建议在 Windows 本地手工安装 Redis 和 MinIO 再联调，因为维护成本更高；本项目已经提供了 `docker-compose.yml`。

## 2. 需要安装的软件

### 2.1 必装

| 软件           | 项目要求       | 用途                                         | 官方下载                                                                      |
| -------------- | -------------- | -------------------------------------------- | ----------------------------------------------------------------------------- |
| Git            | 最新稳定版即可 | 拉代码、更新代码                             | [Git for Windows](https://git-scm.com/download/win)                           |
| JDK            | Java 21        | 运行 `erise-ai-backend` 和 `erise-ai-cloud`  | [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)       |
| Maven          | 3.9+           | 构建和启动 Java 服务                         | [Apache Maven 下载页](https://maven.apache.org/download.cgi)                  |
| Node.js        | 24 LTS         | 构建和启动前端                               | [Node.js 下载页](https://nodejs.org/en/download)                              |
| Docker Desktop | 最新稳定版     | 运行 MySQL / Redis / MinIO，也可直接跑全项目 | [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) |

### 2.2 当前已验证的版本基线

按当前仓库实现，下面这些版本已经验证通过：

- Java 21
- Maven 3.9.14
- Node.js 24.x
- Docker Desktop for Windows

补充说明：

- Node 官方下载页当前提供 `v24.14.1 (LTS)`。
- Maven 官方下载页当前提供 `apache-maven-3.9.14-bin.zip`。
- Docker 官方 Windows 安装文档要求在 WSL 2 后端下使用 `WSL version 2.1.5 or later`，并给出了 Windows 10/11 的最低要求。

## 3. 下载和安装建议

### 3.1 Git

直接安装官方 Windows 版本即可：

- 下载：[Git for Windows](https://git-scm.com/download/win)
- 安装完成后执行：

```powershell
git --version
```

### 3.2 JDK 21

推荐安装 Temurin 21：

- 下载：[Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)
- 安装后确认：

```powershell
java -version
```

预期主版本应为 `21`

### 3.3 Maven

推荐下载 Windows zip 包并解压：

- 下载：[Apache Maven 下载页](https://maven.apache.org/download.cgi)
- 将 `bin` 目录加入系统 `PATH`
- 验证：

```powershell
mvn -v
```

### 3.4 Node.js 24 LTS

- 下载：[Node.js 下载页](https://nodejs.org/en/download)
- 安装完成后验证：

```powershell
node -v
npm -v
```

### 3.5 Docker Desktop

- 下载：[Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/)
- 安装文档：[Install Docker Desktop on Windows](https://docs.docker.com/desktop/setup/install/windows-install/)

安装要点：

- Windows 11 推荐使用 WSL 2 后端
- 安装完成后先启动 Docker Desktop，确保状态为 Running
- 验证：

```powershell
docker version
docker compose version
```

## 4. 获取项目代码

如果你已经在本地有这份仓库，可以跳过。

```powershell
git clone <你的仓库地址> erise-ai
cd erise-ai
```

当前仓库结构如下：

```text
erise-ai-backend/   Spring Boot 业务后端
erise-ai-cloud/     Spring Boot AI 微服务
erise-ai-ui/        Vue 3 前端
deploy/             Nginx 和环境示例
docker-compose.yml  本地基础编排
```

## 确认这些已经装好并可用：

```text
java -version
mvn -v
node -v
docker version
docker compose version
git --version
```

## 5. 环境变量配置

### 5.1 创建 `.env`

在仓库根目录powershell中执行：

```powershell中执行
Copy-Item .env.example .env
```

重点修改这些字段：

```dotenv
<!-- 数据库名称 -->
MYSQL_DATABASE=erise_ai
<!-- 数据库用户名 -->
MYSQL_USER=erise
<!-- 数据库用户密码 -->
MYSQL_PASSWORD=erise_password
<!-- 数据库root密码 -->
MYSQL_ROOT_PASSWORD=root_password
<!-- redis密码 -->
REDIS_PASSWORD=redis_password
<!-- minio管理员用户 -->
MINIO_ROOT_USER=minioadmin
<!-- minio管理员用户密码 -->
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_BUCKET=erise-ai
<!-- JWT_SECRET用来签发登录 token,必须至少 32 个字符 -->
JWT_SECRET=change-this-in-production
INTERNAL_API_KEY
<!-- INTERNAL_API_KEY用来给backend和cloud两个服务互相认证,建议用随机长字符串 -->
INTERNAL_API_KEY=change-this-in-production

OPENAI_API_KEY=你的模型服务密钥
OPENAI_BASE_URL=https://api.openai.com/v1
CHAT_MODEL=gpt-4.1-mini
EMBEDDING_MODEL=text-embedding-3-small

<!-- 前端默认用户账号 -->
ADMIN_USERNAME=admin
<!-- 前端默认用户密码 -->
ADMIN_PASSWORD=Admin123!
<!-- 前端默认用户权限 -->
ADMIN_DISPLAY_NAME=System Admin
```

### 5.2 需要特别注意的变量

- `OPENAI_API_KEY`
  不填也能启动项目，但 AI 问答会失败。

- `OPENAI_BASE_URL`
  如果你用的是 OpenAI 兼容网关，这里改成你的网关地址。

- `JWT_SECRET`
  本地也建议改掉默认值。

- `INTERNAL_API_KEY`
  后端和 AI 微服务之间的内部接口鉴权依赖它，前后必须一致。
- ` JWT_SECRET`和`INTERNAL_API_KEYjoin`
  可以在命令行中使用以下命令生成(两次)
  ```text
  ((65..90) + (97..122) + (48..57) | Get-Random -Count 48 | ForEach-Object {[char]$\_})
  ```

## 6. 数据库初始化和导入说明

### 6.1 默认情况下不需要手工导入 SQL

本项目已经接入 Flyway。

后端启动时会自动执行：

- `erise-ai-backend/src/main/resources/db/migration/V1__init.sql`

也就是说：

- 会自动建表
- 会自动创建全文索引
- 会自动写入默认 `ea_ai_model_config`

所以正常情况下，你**不需要手工导入数据库脚本**。

### 6.2 如果你走 Docker Compose

MySQL 容器会按照 `.env` 自动创建数据库：

- 数据库名：`erise_ai`
- 用户：`erise`
- 密码：`.env` 中的 `MYSQL_PASSWORD`

后端第一次启动时自动跑 Flyway 初始化。

### 6.3 如果你不用 Docker，手工安装 MySQL(不推荐)

请先创建数据库：

```sql
CREATE DATABASE erise_ai
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;
```

再创建业务用户并授权，例如：

```sql
CREATE USER 'erise'@'%' IDENTIFIED BY 'erise_password';
GRANT ALL PRIVILEGES ON erise_ai.* TO 'erise'@'%';
FLUSH PRIVILEGES;
```

然后把 `.env` 或服务环境变量中的数据库连接改成对应值，最后启动后端，Flyway 会自动建表。

### 6.4 如果你要导入你自己的旧数据

先保证旧数据和当前表结构兼容，再导入：

```powershell
mysql -h 127.0.0.1 -P 3306 -u erise -p erise_ai < your_dump.sql
```

注意：

- 旧库表结构必须和当前 `V1__init.sql` 兼容
- 如果已经导入了旧表，再启动 Flyway 时要避免和当前迁移冲突
- 最稳妥的做法是先让 Flyway 初始化空库，再导入符合当前结构的数据

## 7. 推荐启动方式一：全量 Docker

### 7.1 启动

在仓库根目录执行：

```powershell
docker compose up -d --build
```

首次启动会发生这些事：

- 拉取 MySQL / Redis / MinIO / Nginx 镜像
- 构建前端镜像
- 构建业务后端镜像
- 构建 AI 微服务镜像
- 启动所有服务

### 7.2 查看状态

```powershell
docker compose ps
```

### 7.3 访问地址

默认端口如下：

- 前端：`http://localhost:8088`
- Nginx 统一入口：`http://localhost`
- 后端健康检查：`http://localhost:8080/actuator/health`
- AI 微服务健康检查：`http://localhost:8081/actuator/health`
- MinIO Console：`http://localhost:9001`
- MySQL：`localhost:3306`
- Redis：`localhost:6379`

说明：

- 当前 `docker-compose.yml` 同时暴露了 UI 容器和 Nginx
- 实际使用时建议优先通过 `http://localhost` 访问整站

### 7.4 停止

```powershell
docker compose down
```

如果要连数据卷一起删除：

```powershell
docker compose down -v
```

## 8. 推荐启动方式二：混合开发模式

这是我更推荐的本地开发方式。

### 8.1 先启动基础设施

```powershell
docker compose up -d mysql redis minio
```

### 8.2 启动业务后端

```powershell
cd erise-ai-backend
mvn spring-boot:run
```

如果你本机 Maven 全局配置有问题，也可以用仓库里的最小 settings：

```powershell
cd erise-ai-backend
mvn -gs ..\.mvn-settings.xml -s ..\.mvn-settings.xml spring-boot:run
```

### 8.3 启动 AI 微服务

```powershell
cd erise-ai-cloud
mvn spring-boot:run
```

如果 Maven 全局配置异常：

```powershell
cd erise-ai-cloud
mvn -gs ..\.mvn-settings.xml -s ..\.mvn-settings.xml spring-boot:run
```

### 8.4 启动前端

```powershell
cd erise-ai-ui
npm install
npm run dev
```

### 8.5 访问地址

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- AI 微服务：`http://localhost:8081`

## 9. 首次登录和验证

### 9.1 管理员账号

后端启动后会根据 `.env` 自动初始化管理员账号：

- 用户名：`ADMIN_USERNAME`
- 密码：`ADMIN_PASSWORD`

默认值是：

- 用户名：`admin`
- 密码：`Admin123!`

### 9.2 功能验证顺序

建议按下面顺序验证：

1. 打开登录页
2. 获取验证码并登录
3. 创建一个项目
4. 上传一个 `txt` / `md` / `pdf`
5. 等待解析任务完成
6. 新建并发布一篇文档
7. 到搜索页搜索关键词
8. 到 AI 页针对该项目提问
9. 到管理后台查看用户、任务、审计日志

## 10. 常用命令

### 10.1 后端打包

```powershell
cd erise-ai-backend
mvn -gs ..\.mvn-settings.xml -s ..\.mvn-settings.xml -DskipTests package
```

### 10.2 AI 微服务打包

```powershell
cd erise-ai-cloud
mvn -gs ..\.mvn-settings.xml -s ..\.mvn-settings.xml -DskipTests package
```

### 10.3 前端构建

```powershell
cd erise-ai-ui
npm run build
```

### 10.4 前端单测

```powershell
cd erise-ai-ui
npm test -- --run
```

## 11. 常见问题

### 11.1 Docker 启动不了

先确认：

- Docker Desktop 已启动
- WSL 2 已启用
- `docker version` 和 `docker compose version` 能正常输出

### 11.2 登录后 AI 问答失败

重点检查：

- `.env` 里的 `OPENAI_API_KEY` 是否正确
- `OPENAI_BASE_URL` 是否可访问
- `INTERNAL_API_KEY` 在后端和 AI 服务中是否一致

### 11.3 前端能开，但接口全是 401

检查：

- 后端是否真正启动在 `8080`
- 前端 `VITE_API_BASE_URL` 是否正确
- 浏览器本地是否保存了过期 token，必要时清理 localStorage 后重新登录

### 11.4 数据库表没有生成

检查：

- MySQL 是否已启动
- 数据库名是否是 `erise_ai`
- 后端启动日志里 Flyway 是否执行成功
- `MYSQL_*` 环境变量是否正确

### 11.5 文件上传成功但没有进入搜索或 AI

当前只有这些类型会被真正知识化处理：

- `pdf`
- `md`
- `markdown`
- `txt`

其他文件当前只支持：

- 上传
- 元数据保存
- 下载

## 12. 推荐的本地使用顺序

如果你只是想最快跑起来：

1. 安装 Git / JDK 21 / Maven / Node.js 24 / Docker Desktop
2. 复制 `.env.example` 为 `.env`
3. 填好 `OPENAI_API_KEY`
4. 执行 `docker compose up -d --build`
5. 打开 `http://localhost`
6. 用默认管理员登录
7. 创建项目并上传 `txt/md/pdf`

如果你是要本地开发调试：

1. `docker compose up -d mysql redis minio`
2. 本机启动 `erise-ai-backend`
3. 本机启动 `erise-ai-cloud`
4. 本机启动 `erise-ai-ui`
5. 在 `http://localhost:5173` 调试

## 官方参考链接

- [Node.js Download](https://nodejs.org/en/download)
- [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21)
- [Apache Maven Download](https://maven.apache.org/download.cgi)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Docker Desktop for Windows 安装文档](https://docs.docker.com/desktop/setup/install/windows-install/)
- [Git for Windows](https://git-scm.com/download/win)
