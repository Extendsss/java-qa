# 刷题系统

依据 [刷题系统设计文档](刷题系统设计文档.md) 实现的内部面试练习系统。后端采用 **JDK 8 / Spring Boot 2.7.18 / MyBatis 2.3.2 / Sa-Token 1.38.0 / MySQL 8**；前端采用 **Vue 3、Vue Router 4、Axios CDN**，由同一个 Spring Boot 进程提供静态资源，无需 Node 构建。

## 功能

- 封闭账号体系：管理员创建账号、调整角色、禁用、重置密码；用户修改自己的密码。密码采用 BCrypt。禁用与重置密码会踢下线。
- 公共题库与个人私有题库，支持问答、单选、多选、判断、填空，以及多级问题链。
- 随机、顺序、错题练习；客观题判分、问答题自评；答题记录、最近一次作答决定的错题本、分类统计。
- 题目评论；题目、简历等内容均软删除。
- 结构化简历专项：动态模板、可重复分块、分页预览、关联分块的问题链、整份简历练习。
- 管理后台：用户、分类、全站题目、简历、分块模板管理；整链/整份简历转公共或转回私有。
- 普通用户只能编辑自己的私有内容；转公共后由管理员维护。服务端统一执行可见性和编辑权限检查。

## 本地运行

准备 JDK 8、Maven 3.6+、MySQL 8。建议使用仍有安全更新的 JDK 8 发行版。数据库用户需要建表与读写权限。

在 MySQL 中创建数据库（默认本地 JDBC URL 也会尝试自动建库）：

```sql
CREATE DATABASE IF NOT EXISTS quiz_system
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

PowerShell 中设置数据库连接后启动：

```powershell
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '你的数据库密码'
# 如地址、端口、库名不同，请同时设置 DB_URL：
# $env:DB_URL = 'jdbc:mysql://localhost:3306/quiz_system?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
mvn spring-boot:run
```

浏览器访问 <http://localhost:8080>。默认端口为 8080，可通过 `PORT` 环境变量调整。

首次启动自动执行 `src/main/resources/schema.sql`，创建不存在的表，并初始化管理员、五个分类、六个简历分块和覆盖五种题型的示例题；已有数据不会重复插入。默认初始管理员为 **admin / admin123**。可在首次启动前通过 `ADMIN_USERNAME`、`ADMIN_PASSWORD` 指定初始管理员；这些变量只影响首次创建，之后改密码请使用页面或接口。

`.env.example` 仅作为配置示例，Spring Boot 不会自动加载 `.env`。实际 Aiven 凭据保存在本地 `数据库配置.md`，该文件已从 Git 和 Docker 构建上下文排除，不写入 `application.yml` 或 JAR。

前端使用 Vue 3、Vue Router 4 和 Axios 的浏览器发行包，无需 Node 构建。页面优先从 CDN 加载；CDN 请求失败时，会按依赖顺序同步回退加载项目内 `static/vendor` 的相同版本。第三方 MIT 许可证保留在该目录。页面采用 hash 路由，例如 `/#/questions`，刷新无需代理 fallback。

## 构建与验证

```powershell
mvn -B verify
mvn -B -DskipTests package
java -jar target/quiz-system-0.0.1-SNAPSHOT.jar
```

构建产物是可执行 JAR。运行需要 MySQL；测试环境由测试配置管理。GitHub Actions 在 push / pull request 时使用 Java 8 执行 `mvn -B verify`。

如本机安装了 Node.js，可运行 `node scripts/check-frontend.cjs`。该脚本使用项目随附的 Vue 编译器检查全部 21 个组件模板和 7 个应用脚本，不需要 npm install；浏览器运行本身不依赖 Node.js。

账号与权限单元测试覆盖登录失败锁定和用户名归一化、成功后的失败计数清理、私有内容隔离、公共内容编辑权回收、角色变更即时读取、禁用账号拒绝登录、管理员自禁用/自降级保护、BCrypt 入库以及响应不泄露密码。后端集成测试进一步检查数据库与接口行为。

本次在 JDK 8 下通过全部 51 个自动化测试，并在真实 MySQL 8 上通过 5 个接口集成场景和 87 条简历/模板 API 断言；具体环境、重复验证方式及浏览器验收边界见 [验证记录](docs/verification.md)。`scripts/smoke-resumes.ps1` 可针对专用测试环境重复检查简历与模板功能。

## 配置

| 环境变量 | 默认值 / 用途 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 默认 `dev`，生产设为 `prod` |
| `PORT` | `8080` |
| `DB_URL` | dev 默认本地 MySQL；prod 必填完整 JDBC URL |
| `DB_USERNAME` | dev 默认 `root`；prod 必填 |
| `DB_PASSWORD` | dev 默认 `root`；prod 必填 |
| `ADMIN_USERNAME` | 首次创建管理员的用户名，默认 `admin` |
| `ADMIN_PASSWORD` | 首次创建管理员的密码，默认 `admin123` |

鉴权使用 `satoken` 请求头，token 有效期七天，每次登录生成新 token；内存保存登录态，不依赖 Redis。修改自己的密码后需要重新登录；进程重启后所有登录态失效。

## Aiven 与 Render 部署

### 使用项目中的本地 Aiven 配置

根目录 `数据库配置.md` 使用 `Database name`、`Host`、`Port`、`User`、`Password` 五个键，每行以冒号分隔。以下脚本读取该文件，通过进程环境变量传递连接信息，并强制 `sslMode=REQUIRED`，不会打印密码或将其放入 Java/Maven 命令行。

```powershell
# 启动连接真实 Aiven 数据库的应用；默认仅监听本机。
.\scripts\start-aiven.ps1 -Port 18081

# 对真实 Aiven 数据库执行自动化测试，需要 Maven 和 JDK 8。
.\scripts\test-aiven.ps1

# 应用运行后，检查简历与模板的完整 HTTP 流程。
.\scripts\smoke-resumes.ps1 -BaseUrl 'http://127.0.0.1:18081'
```

如果 Maven 不在 PATH，可通过 `test-aiven.ps1 -MavenCommand 'mvn.cmd 的完整路径'` 指定；需要时设置 `JAVA_HOME` 指向 JDK 8。脚本支持 `-ConfigPath` 选择另一份本地配置。真实库测试只操作本轮新建的实体，结束后软删除测试题目、简历、评论和自定义模板，禁用测试账号并注销会话；答题记录保留审计，不物理删数据。管理员密码如已变更，请用环境变量 `QUIZ_TEST_ADMIN_USERNAME`、`QUIZ_TEST_ADMIN_PASSWORD` 配置 Java 集成测试，并给 HTTP 脚本传入对应管理员参数。

本次 Aiven 建表、初始化与验证结果见 [Aiven 验证记录](docs/aiven-verification.md)。

### 云端部署

1. 在 Aiven 创建 MySQL 8 实例和数据库，取得地址、端口、用户名、密码。
2. 将 JDBC URL 配为 `jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai`。数据库须事先存在，账号须具备建表权限。
3. 将代码提交到自己的 GitHub 仓库，在 Render 创建 Docker Web Service，连接仓库；也可使用仓库内 `render.yaml` Blueprint。
4. 配置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`ADMIN_PASSWORD`，以及 `SPRING_PROFILES_ACTIVE=prod`。Render 自动提供 `PORT`。
5. 健康检查路径设为 `/api/ping`。Dockerfile 使用 Maven / JDK 8 构建并在 JRE 8 中运行。

如部署平台支持自备 Java/Maven，也可执行 `mvn -B -DskipTests package`，再用 `java -jar target/quiz-system-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod` 启动。Render 采用 Docker 路径，以确保 Java 8 和 Maven 环境明确可用。

Render 免费实例可能休眠，首次请求需要等待冷启动；重启会使 token 失效。持久数据全部存于 MySQL，应用无需持久磁盘。

## 宝塔面板（阿里云专享版）部署

在阿里云 ECS + 宝塔 Linux 面板环境部署本项目的完整操作步骤（安全组放行、Nginx / MySQL 8 / JDK 8 安装、Java 项目管理器运行 jar、反向代理与 HTTPS）、上线验收清单、发版升级 SOP 与常见问题排查，见 [宝塔部署手册](docs/baota-deploy.md)。

## API 约定

统一响应：`{"code":0,"message":"ok","data":...}`。业务错误也可能使用 HTTP 200，请按 `code` 判断；401 表示需重新登录，403 表示无权限。分页接口返回 `{"total":0,"list":[]}`，`page` 从 1 开始，`size` 默认 10、最多 100。

```powershell
$response = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/auth/login' -ContentType 'application/json' -Body '{"username":"admin","password":"admin123"}'
$headers = @{ satoken = $response.data.token }
Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/me' -Headers $headers
Invoke-RestMethod -Uri 'http://localhost:8080/api/questions?page=1&size=10' -Headers $headers
```

登录与 `/api/ping` 免鉴权，其余 `/api/**` 均要求登录，`/api/admin/**` 要求管理员角色并有注解校验。没有注册、找回密码或删除用户接口。完整接口、字段和业务规则见设计文档。

问答题查看参考答案与最终自评分两个阶段，最终提交必须携带 `selfCorrect: 0` 或 `1`；仅最终自评计入记录。多选字母去重排序后比较，填空以 `||` 分隔并逐空去首尾空白、忽略大小写。

## 目录

```text
src/main/java/com/quiz/
  common/        统一响应、异常、分页与校验
  config/        Sa-Token 路由鉴权、种子数据
  security/      角色查询、会话权限、登录限流
  controller/    REST API（admin/ 为管理接口）
  service/       业务规则与事务
  mapper/        MyBatis 接口
  entity/        数据实体与请求 DTO
src/main/resources/
  mapper/        XML SQL（参数绑定）
  static/        Vue 页面与样式
  schema.sql     MySQL 建表脚本
  application*.yml
```

建表脚本用于新部署的幂等建表，不提供已有数据库的版本迁移。部署到已有库前，请先备份并核对表结构。
