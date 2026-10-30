# 宝塔 Linux 面板（阿里云专享版）部署手册

> 适用项目：quiz-system（内部刷题与结构化简历面试练习系统）
> 技术栈：Spring Boot 2.7.18 · JDK 8 · MySQL 8 · 可执行 jar（前端打包在 jar 内）
> 部署方式：宝塔面板 Java 项目管理器 + Nginx 反向代理 + HTTPS
> 最后更新：2026-10-30

## 1. 部署架构

```text
浏览器 ── HTTPS ──▶ Nginx（80 / 443）
                      │ 反向代理
                      ▼
              Spring Boot jar（127.0.0.1:8080）
                      │ JDBC
                      ▼
              MySQL 8（127.0.0.1:3306）
```

- 前端 Vue 页面打包在 jar 的 static 目录内，无需 Node 构建、无需单独部署前端
- 路由采用 hash 模式（如 `/#/questions`），页面刷新不依赖 Nginx 的 fallback 配置
- 表结构随应用启动自动创建（`schema.sql` 幂等建表），无需手动导表
- 生产配置（数据库连接、初始管理员）通过环境变量注入，见第 8 步

## 2. 前提条件

- 已购阿里云 ECS 并安装宝塔 Linux 面板（阿里云专享版），建议 2 核 2G 及以上
- 有一个域名（没有也能先用 IP 访问，HTTPS 后补）
- 本地装好 JDK 8 与 Maven 3.6+（用于打包），以及 SSH 工具（也可全程用面板文件管理）

## 3. 第一步：登录面板并做基础安全设置

1. 阿里云控制台 →「云市场 → 已购买的服务」→ 宝塔面板 →「管理 / 打开面板」。专享版通过阿里云云助手免端口登录，无需放行 8888 端口
2. 首次进入按提示绑定宝塔账号，设置面板用户名与密码（与阿里云账号相互独立）
3. 面板「面板设置」中建议开启：绑定访问域名、限制授权 IP、两步验证（Google Authenticator）；非专享版入口访问时记得修改面板默认端口

## 4. 第二步：放行端口

两道闸门都要放行：阿里云安全组 + 宝塔系统防火墙。

| 端口 | 用途 | 策略 |
| ---- | ---- | ---- |
| 22 | SSH 远程登录 | 放行，建议限制来源 IP |
| 80 | HTTP | 放行（申请证书需要） |
| 443 | HTTPS | 放行 |
| 8080 | 应用端口 | **不放行公网**，只由 Nginx 反代走 127.0.0.1 |
| 3306 | MySQL | **禁止放行公网**，应用走本机连接 |
| 面板端口 | 宝塔面板 | 专享版免端口，无需处理 |

操作路径：

1. 阿里云 ECS 控制台 → 实例 →「网络与安全 → 安全组」→ 入方向规则，添加 80、443（22 一般已有）
2. 宝塔面板「安全」→ 系统防火墙，放行 80、443

## 5. 第三步：安装运行环境

宝塔「软件商店」安装以下三件套：

| 软件 | 版本建议 | 说明 |
| ---- | ---- | ---- |
| Nginx | 最新稳定版 | 反向代理与 HTTPS |
| MySQL | 8.0 | 项目按 MySQL 8 验证 |
| Java 项目管理器 | 最新版 | 安装过程中勾选 JDK 1.8 |

- MySQL 装完记好 root 密码
- 不需要安装 PHP、FTP 等，站点类型用「纯静态」即可

## 6. 第四步：创建数据库

1. 面板「数据库」→ 添加数据库：
   - 数据库名：`quiz_system`
   - 字符集：`utf8mb4`（排序规则用默认即可）
   - 用户名：单独建一个如 `quiz`，密码用强密码并记下
2. **不需要导入任何 SQL**：应用启动时自动执行 `schema.sql` 创建全部数据表（均为 `CREATE TABLE IF NOT EXISTS`，重复启动不丢数据），并初始化管理员账号、分类、简历分块模板与示例题
3. 该建表脚本只做幂等建表，不提供已有库的表结构升级迁移；以后若改用已有数据库，请先备份并核对表结构

## 7. 第五步：本地打包并上传

打包（IDEA 里双击 Maven 面板的 package 也一样）：

```bash
mvn -B -DskipTests package
```

产物：`target/quiz-system-0.0.1-SNAPSHOT.jar`

上传到服务器 `/www/quiz-system/`（面板「文件」里新建该目录后上传，或用 scp）：

```bash
scp target/quiz-system-0.0.1-SNAPSHOT.jar root@服务器IP:/www/quiz-system/
```

## 8. 第六步：用 Java 项目管理器运行 jar（核心）

面板「网站 → Java 项目」→ 新建项目，逐项填写：

| 配置项 | 填写值 |
| ---- | ---- |
| 项目名称 | quiz-system |
| 项目路径 | /www/quiz-system |
| 项目 jar | quiz-system-0.0.1-SNAPSHOT.jar |
| JDK 版本 | 1.8 |
| 端口 | 8080 |
| 启动参数 | --spring.profiles.active=prod |
| 开机启动 | 开 |
| 守护进程 | 开（崩溃自动拉起） |

在「环境变量 / 启动参数」区域逐行填写环境变量（不同面板版本入口名称略有差异，找带"环境变量"的输入框）：

| 变量 | 示例值 / 说明 |
| ---- | ---- |
| DB_URL | jdbc:mysql://127.0.0.1:3306/quiz_system?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true |
| DB_USERNAME | quiz（第六步建的库账号） |
| DB_PASSWORD | 对应密码 |
| ADMIN_USERNAME | 初始管理员用户名，默认 admin |
| ADMIN_PASSWORD | 初始管理员密码，**必须改成强密码**，默认 admin123 不可上线 |
| PORT | 一般不填，默认 8080；改了要同步改 Nginx |

要点：

- `allowPublicKeyRetrieval=true` 必须带：MySQL 8 默认 caching_sha2_password 加密，缺它会报 Public Key Retrieval is not allowed
- `ADMIN_USERNAME` / `ADMIN_PASSWORD` **只在首次启动创建管理员时生效**，之后改密码请走页面功能
- prod 配置下 DB_URL、DB_USERNAME、DB_PASSWORD 均无默认值，漏配会直接启动失败（故意为之，防止连错库）

启动后在项目日志中看到类似两行即成功：

```text
Started QuizSystemApplication in x.xxx seconds
Tomcat started on port(s): 8080
```

服务器上本机验证（`/api/ping` 免鉴权）：

```bash
curl http://127.0.0.1:8080/api/ping
```

返回 `{"code":0,...}` 即正常。再验证登录：

```bash
curl -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"你的密码"}'
```

## 9. 第七步：Nginx 站点与反向代理

1. 面板「网站」→ 添加站点：域名填自己的域名，PHP 版本选「纯静态」
2. 站点设置 → 反向代理 → 添加反向代理，目标 URL 填 `http://127.0.0.1:8080`，发送域名 `$host`
3. 生成的配置大致如下（可在站点配置文件中核对）：

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_http_version 1.1;
}
```

4. 建议在站点配置中开启 gzip（text/css、application/javascript、application/json）

## 10. 第八步：域名解析与 HTTPS

1. 域名 DNS 控制台添加 A 记录：主机记录 `@` 或自定前缀，记录值为服务器公网 IP
2. 站点设置 → SSL → Let's Encrypt，勾选域名申请免费证书（要求 80 端口正常）
3. 开启「强制 HTTPS」，宝塔会自动续期证书
4. 浏览器访问 `https://你的域名`，应看到系统登录页

## 11. 上线验收清单

- [ ] `curl http://127.0.0.1:8080/api/ping` 返回 `{"code":0,...}`
- [ ] 域名 HTTP 自动跳转 HTTPS，浏览器小锁正常
- [ ] 初始管理员登录成功，题库、练习、简历页面正常打开
- [ ] 创建一个普通账号登录，验证只能看到自己的私有内容
- [ ] 重启一次服务器，应用自动拉起、数据完好
- [ ] 安全组确认没有 3306、8080 的公网放行规则
- [ ] admin123 已替换为强密码

## 12. 安全加固与日常运维

安全加固：

- 管理员与用户密码都用强密码；系统是封闭账号体系，无注册接口，账号由管理员发放
- MySQL 只走 127.0.0.1，3306 不出公网；数据库账号不用 root
- 面板保持：绑定域名 / 限制授权 IP / 两步验证
- SSH 改密钥登录并限制来源 IP（安全组即可限制）

备份（面板「计划任务」）：

| 任务 | 频率 | 内容 |
| ---- | ---- | ---- |
| 备份数据库 | 每天 | quiz_system 全库 |
| 备份目录 | 每周 | /www/quiz-system |
| 备份外送 | 每周 | 下载到本地或转存 OSS |

发版升级 SOP：

1. 本地改完代码执行 `mvn -B -DskipTests package`
2. 上传新 jar 到 /www/quiz-system/（旧 jar 先改名备份，如 quiz-system-20261030.jar）
3. Java 项目管理器里重启项目
4. `curl /api/ping` 加页面冒烟验证
5. 两个注意点：重启后**所有登录态失效**（token 存内存），请挑低峰发版；若有表结构变更需自行整理 SQL 执行，schema.sql 不负责存量升级

## 13. 常见问题排查

**外网完全打不开**：按链路顺序查四层：① 阿里云安全组 80/443 → ② 宝塔防火墙 80/443 → ③ Java 项目是否在运行（Java 项目管理器状态与日志）→ ④ Nginx 反代目标是否 `127.0.0.1:8080`

**页面 502 Bad Gateway**：Java 项目没起来或端口不一致。看 Java 项目日志，`netstat -tlnp | grep 8080` 确认端口监听

**启动报数据库 Access denied**：核对 DB_USERNAME / DB_PASSWORD，确认该用户对 quiz_system 有权限（面板建库会自动授权）

**启动报 Public Key Retrieval is not allowed**：DB_URL 缺 `allowPublicKeyRetrieval=true` 参数

**页面中文乱码**：建库时字符集没选 utf8mb4。备份后按第六步重建，或逐表转换字符集

**忘记管理员密码**：所有管理员都进不去时：停服 → 备份 → 删除 `sys_user` 中管理员行 → 配好 ADMIN_USERNAME / ADMIN_PASSWORD 环境变量后重启，初始管理员会重新创建

**想换应用端口**：改 PORT 环境变量并重启，Nginx 的 proxy_pass 同步修改

**临时用 IP:8080 直连调试**：安全组临时放行 8080，调试完立刻删除规则

## 14. 附录：不用面板的 systemd 方式（可选）

不想依赖 Java 项目管理器时，用 systemd 守护也完全可以：

```ini
[Unit]
Description=quiz-system
After=network.target mysql.service

[Service]
WorkingDirectory=/www/quiz-system
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_URL=jdbc:mysql://127.0.0.1:3306/quiz_system?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
Environment=DB_USERNAME=quiz
Environment=DB_PASSWORD=你的数据库密码
Environment=ADMIN_PASSWORD=你的初始管理员密码
ExecStart=/usr/bin/java -jar /www/quiz-system/quiz-system-0.0.1-SNAPSHOT.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

保存为 `/etc/systemd/system/quiz-system.service` 后执行：

```bash
systemctl daemon-reload
systemctl enable --now quiz-system
```

看日志：`journalctl -u quiz-system -f`
