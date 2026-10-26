# Aiven 真实数据库建表与测试记录

验证日期：2026-10-26。使用项目根目录本地 `数据库配置.md` 中的真实连接信息，目标为 Aiven `defaultdb`，MySQL **8.4.11**。操作前数据库中没有业务表。本记录不包含数据库密码、主机或账号凭据。

## 建表与初始化

已直接在真实数据库创建全部 8 张表：`sys_user`、`question_category`、`block_def`、`resume`、`resume_block`、`question`、`answer_record`、`comment`。

核验结果：

- 全部表使用 InnoDB、`utf8mb4_unicode_ci`；已在正式 `schema.sql` 中明确指定排序规则，避免继承 Aiven 默认的另一种排序规则。
- `block_def.field_schema`、`resume_block.content`、`question.options` 均为真实 MySQL JSON 列。
- 主键、唯一索引和查询索引均已建立，共 25 个索引。
- 初始化生成 1 个管理员、5 个分类、6 个预置简历分块、7 道示例题，包含五种题型与多级问题链。
- MySQL 客户端实测使用 TLS 1.3，密码套件 `TLS_AES_256_GCM_SHA384`。JDK 8 应用和测试连接使用 `sslMode=REQUIRED`，均已成功读写真实数据库。

## 测试结果

| 检查 | 结果 |
| --- | --- |
| JDK 8 下完整自动化测试，真实库使用 prod profile | 51 个测试全部通过，0 失败、0 错误、0 跳过，BUILD SUCCESS |
| 其中真实 Aiven 的控制器集成场景 | 5 个全部通过，包含真实 XML SQL、事务、JSON、鉴权与数据写入 |
| 简历与模板 HTTP 业务验收 | 88 条业务断言全部通过 |
| HTTP 验收后的数据及会话清理 | 31 条清理断言全部通过，脚本退出码 0 |
| 修改后的集成测试在默认 H2 下回归并打包 | 5 个集成场景通过，BUILD SUCCESS |
| 本地应用连接 Aiven | 健康、登录、普通题库与简历模板读取正常 |

自动化覆盖账号禁用与踢下线、私有题隔离、公共题编辑锁、整链公开/转私、子树软删除、brief 隐藏答案、QA 两阶段自评、客观题判分、最新答题记录决定的错题本、个人统计、评论权限，以及简历与问题链归属和级联。

HTTP 验收另外覆盖模板 20 个上限、预置/引用删除保护、字段白名单、必填、数据类型、列表长度、真实日期、单条与可重复约束、停用后保留已有内容、跨简历关联拒绝、普通练习池排除简历题、整份公开后的只读权限和转私恢复。

## 测试后的数据库状态

| 数据 | 总记录数 | 有效记录数 |
| --- | ---: | ---: |
| 用户 | 13 | 1 |
| 分类 | 5 | 5 |
| 分块定义 | 20 | 6 |
| 题目 | 18 | 7 |
| 简历 | 3 | 0 |
| 简历分块内容 | 4 | 0 |
| 评论 | 1 | 0 |

12 个测试账号均已禁用；测试题目、简历、分块、评论和自定义模板均已软删除。额外 SQL 核验确认启用的测试账号为 0、有效测试题目为 0。保留了 3 条答题审计记录，其关联题目已软删除，不进入有效统计。原有初始化管理员、5 个分类、6 个预置分块和7道示例题均保留。

测试不物理删除业务数据，也不修改既有管理员的密码或角色。测试账号使用独立标记和返回的实体 ID 进行清理；注销管理员时只注销本次签发的会话。

## 重复运行

```powershell
# 从本地配置读取连接信息并启动真实库应用
.\scripts\start-aiven.ps1 -Port 18081

# 真实库自动化测试
.\scripts\test-aiven.ps1

# HTTP 简历与模板验收，应用须已经启动
.\scripts\smoke-resumes.ps1 -BaseUrl 'http://127.0.0.1:18081'
```

Maven 未配置在 PATH 时，可用 `-MavenCommand` 指定 `mvn.cmd` 完整路径；如已经缓存依赖可加 `-Offline`。测试初始管理员默认 `admin / admin123`，如已修改密码，可通过 README 中说明的环境变量或脚本参数指定。

本地证据文件位于 `target/aiven-tests.log`、`target/aiven-surefire-reports/`、`target/aiven-smoke.log`、`target/aiven-db-verification.txt` 和 `target/aiven-h2-regression.log`。这些文件为本轮运行产物，不打包发布。

真实凭据文件已加入 `.gitignore` 和 `.dockerignore`。应用配置继续使用环境变量占位符，凭据不进入源码、命令行参数、测试断言输出或 JAR。
