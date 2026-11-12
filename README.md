# 某专业师生业绩管理系统

面向高校单个专业的课程项目，管理教师教学、科研和指导成果，以及学生竞赛、奖学金、专利、论文、软件著作权和行业认证。采用 Java + Vue，提供本地演示与 MySQL 部署两条运行路线。

## 技术路线

- 后端：Java 21、Spring Boot 4.0.6、Spring Security、MyBatis Starter 4.0.1、Apache POI。
- 前端：Vue 3.5、TypeScript 5.9、Vite 7、Element Plus 2.11、ECharts；具体版本由依赖锁文件记录。
- 演示数据库：H2 文件数据库，首次运行自动建表和生成演示数据，无需安装 MySQL。
- 部署数据库：MySQL 8.4；前端构建后打入后端，8080 端口同源访问。
- 架构：前后端分离开发、模块化单体部署；Session 登录、CSRF 防护、服务端数据权限，材料私有存储。

## 手动运行

前端已包含在现有运行包中。本地 MySQL 配置项为 `SPRING_PROFILES_ACTIVE=mysql`、`DB_URL`、`DB_USERNAME` 与 `DB_PASSWORD`；配置好当前终端环境后，从 `backend` 目录用 Java 21 运行：

```powershell
Set-Location backend
java -jar .\target\achievement-manager-1.0.0.jar --server.address=127.0.0.1 --server.port=8080
```

然后打开 [http://localhost:8080](http://localhost:8080)。在终端按 `Ctrl+C` 可停止服务。MySQL 密码不要写入源码或保存在命令历史中。

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `Admin@12345` |
| 教师 | `teacher` | `Teacher@12345` |
| 学生 | `student` | `Student@12345` |

上述账号仅供本地演示。演示数据只在初始化条件满足时创建，不会在每次启动时重置已有数据。

修改前端代码后，进入 `frontend` 目录运行 `npm run build`；将构建产物放入 `backend/src/main/resources/static`，再进入 `backend` 目录运行 `mvn package` 更新 JAR。


## 功能范围

| 模块 | 首版功能 |
| --- | --- |
| 账号与权限 | 管理员、教师、学生；管理员管理账号；个人修改密码 |
| 成果管理 | 8 类成果、级别与日期、分类详情、多人参与、指导教师、条件筛选 |
| 审核 | 草稿 → 待审 → 通过/退回；退回意见；禁止审核自己的关联成果 |
| 支撑材料 | 成果下上传、下载、删除材料；权限跟随成果，审核后禁止直接修改 |
| 认证支撑 | 管理指标，将成果关联至指标，按指标检索和导出 |
| 统计 | 已通过成果总数、类别/级别/年度/月度分布、参与及指导关系统计 |
| 导出 | Excel 成果台账、ZIP 材料包；默认导出已通过成果 |

成果以唯一记录计数，同一记录的多个参与人不会增加专业成果总数。系统没有跨记录的智能查重，录入同一奖项时应复用已有记录。普通用户仅能查看自己拥有、参与或指导的成果；参与人能查看，修改仍需要所有者或管理员权限。

界面已更新：支持 `Ctrl/Cmd+K` 快速导航、统计明细跳转、可恢复的组合筛选、手机成果卡片、未保存修改保护和材料上传队列。全部资源仍可离线使用，更新范围与实测记录见 [UI 与交互更新](docs/UI与交互更新.md)。

首版使用自然年统计；材料按关联成果分类。批量导入、学年口径、跨专业管理、审批多级流转、成果全文历史版本、自动计算毕业要求达成度属于后续增强，不在本次交付范围。

已完成前端生产构建、后端 9 项自动化测试，以及 H2 / MySQL 8.0.46 两个独立数据库上的真实 HTTP 验证（各 12 组通过）。打包后的浏览器实测已完成学生登录、录入、材料上传、提交、管理员审核、归档与子页面刷新；仪表盘显示审核后更新的统计。内嵌浏览器未捕获下载事件，导出文件内容已通过 HTTP 和后端测试。Compose 采用 MySQL 8.4，当前机器未安装 Docker，该容器路线未实测。证据与覆盖范围见 [测试报告](docs/04-测试计划与报告.md)。

核心业务验证的已执行结果和覆盖范围见[测试报告](docs/04-测试计划与报告.md)。本项目中的启动、构建及一次性验证入口文件已经清理。

## 使用本机 MySQL（无需 Docker）

已安装 MySQL 时，可以直接使用本机数据库和已打包的应用；此路线只需 Java 21 与正在运行的 MySQL，不需要安装 Docker、Node.js 或 Maven。

先准备名为 `achievements` 的数据库，字符集使用 `utf8mb4`。启动脚本只连接现有库，不负责建库，也不自动把 H2 中的成果、账号和材料迁移到 MySQL。

本机原 H2 数据已于 2026 年 10 月 17 日完成一次增量迁移，当前 MySQL 中有 25 项成果、19 条审核记录和 1 份附件，原文件和迁移前备份均保留。详细备份位置、核对结果和时间精度处理见 [数据迁移记录](docs/数据迁移记录.md)。后续两个数据库之间不会自动同步。

本机数据库地址为 `127.0.0.1:3306`，项目库名为 `achievements`，数据库用户默认为 `root`。页面账号仍使用系统账户，与数据库凭据分开。连接配置和运行参数见[环境变量说明](#数据保存与配置)。MySQL 与 H2 的数据不会自动同步。

如需单独开发前端，可在 `frontend` 目录运行 `npm run dev`，然后访问 [http://127.0.0.1:5173](http://127.0.0.1:5173)。

## 开发模式

分别打开两个终端：

```powershell
# 终端 1
Set-Location backend
mvn spring-boot:run -Dspring-boot.run.arguments=--server.address=127.0.0.1
```

```powershell
# 终端 2
Set-Location frontend
npm install
npm run dev
```

开发页面为 [http://127.0.0.1:5173](http://127.0.0.1:5173)，Vite 将 `/api` 转发至 8080。生产构建由后端提供页面，浏览器刷新子页面时由后端回退到 SPA 入口。

## Docker Compose 部署

需要 Docker Engine / Docker Desktop 和 Compose v2。部署步骤：

1. 将 `.env.example` 复制为 `.env`。
2. 填写不同的 `MYSQL_ROOT_PASSWORD`、`DB_PASSWORD`、`INITIAL_ADMIN_PASSWORD`；初始管理员密码至少 8 位，包含字母和数字。
3. 执行以下命令。

```powershell
docker compose up --build -d
docker compose logs -f app
```

访问 [http://localhost:8080](http://localhost:8080)。部署模式关闭演示种子数据，空数据库使用环境变量创建初始管理员。账号配置在 `INITIAL_ADMIN_USERNAME`（默认 `admin`），密码为自己填写的 `INITIAL_ADMIN_PASSWORD`；已有管理员时不会借启动配置重置其密码。

Compose 将应用端口和 MySQL 端口绑定到宿主机 `127.0.0.1`。对外服务时，通过配置 HTTPS 的反向代理访问应用，设置 `COOKIE_SECURE=true`；按实际服务器防火墙与域名配置开放入口。具体步骤见 [部署说明](deploy/README.md)。

```powershell
docker compose down
```

停止不会删除命名卷。数据库位于 `mysql-data` 卷，支撑材料位于 `materials-data` 卷。不要对需要保留数据的环境使用 `down -v`。

## 数据保存与配置

脚本从 `backend` 目录启动，本地 H2 文件与材料位于 `backend/.data/`。备份时先正常停止服务，再同时复制整个目录；仅备份数据库文件不能恢复附件。MySQL 环境需同时备份数据库与材料卷。

| 环境变量 | 作用 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=mysql` | 启用 MySQL 配置；未指定时使用 H2 |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 数据库连接 |
| `MATERIALS_DIR` | 支撑材料实际存储目录 |
| `SEED_DEMO` | 是否初始化演示数据；H2 默认 true，MySQL 默认 false |
| `INITIAL_ADMIN_USERNAME`、`INITIAL_ADMIN_PASSWORD` | 空用户库首次创建初始管理员（关闭演示种子时使用） |
| `SERVER_ADDRESS`、`PORT` | 应用绑定地址与端口；默认 8080 |
| `COOKIE_SECURE` | HTTPS 环境设 true；本地 HTTP 保持 false |

## 项目文件与报告

```text
backend/                 Java 后端、数据库建表脚本与测试
frontend/                Vue 页面与图表
docs/                    中文需求、设计、分工、测试、用户手册、AI 记录
deploy/                  Docker 构建及部署说明
CONTRACT.md              前后端 API 与协作约定
docker-compose.yml       MySQL 与应用容器
```

- [需求分析](docs/01-需求分析.md)
- [完整项目报告](docs/项目报告.md)
- [总体设计与数据库设计](docs/02-总体设计与数据库设计.md)
- [开发路线与 8 人分工](docs/03-开发路线与分工.md)
- [测试计划与报告](docs/04-测试计划与报告.md)
- [用户手册](docs/05-用户手册.md)
- [AI 辅助开发记录](docs/06-AI辅助开发记录.md)

演示数据库中数据为虚构样例，不能作为实际认证或评估依据。正式使用应录入真实、经审核且有支撑材料的成果，并完成本校统计口径核对。
