# 应大 Wiki 资料投稿 Demo

该单体应用适用于现有静态 Wiki。投稿和审核数据保存在 MySQL，待审附件保存在私有磁盘目录；审核通过后生成独立 HTML 和下载附件。可选配置一个**独立测试仓库**，一次 GitHub Git Database API commit 同时更新页面、附件和投稿目录索引。本 Demo 会拒绝向 `uem-wiki/uem_wiki` 正式仓库发布。

## 版本和依赖

- JDK 8
- Spring Boot 2.7.18
- MyBatis-Plus 3.5.5
- MySQL 8.0+
- Maven 3.6+
- Node.js 20+
- Python 3.7+（本机独立静态预览；Wiki 原构建器也使用 Python）

项目原 Wiki 继续使用 Python 3.7+ 构建 Markdown；提供的 `build_wiki.py` 是 Markdown 转 HTML 构建器，不是 Word 转换器。投稿发布由 Java `StaticPublisher` 读取 `WIKI_ROOT/templates/base.html`，沿用 Wiki 的页面结构，并复制样式、导航脚本、导航数据和 `圆形logo.png` 等公开资源到导出目录；GitHub 测试发布会将页面、附件、目录入口和这些页面依赖资源放进同一个提交。投稿正文仍按安全转义后的纯文本显示，DOCX/PDF/PPTX 保持下载附件，不转换文档内容。

## Windows + IDEA 启动

1. 安装 JDK 8、Maven 3.6+、MySQL 8；IDEA 使用项目 SDK JDK 8 导入本目录 `pom.xml`。
2. 在 MySQL Workbench 执行 `src/main/resources/db/init.sql`。
3. 将 `src/main/resources/application.yml.example` 复制为 `src/main/resources/application.yml`，按需修改数据库地址和用户名。真实密码不要提交。
4. 设置运行环境变量：

   ```powershell
   $env:DB_USER="uemwiki"
   $env:DB_PASSWORD="本机数据库密码"
   $env:ADMIN_USER="wiki-admin"
   $env:ADMIN_PASSWORD_HASH="使用下方命令生成的 BCrypt 值"
   $env:UPLOAD_DIR="D:\YingDaWiki-private\uploads"
   $env:EXPORT_DIR="D:\YingDaWiki-export"
   $env:WIKI_ROOT="D:\YingDaWiki\uem_wiki"
   ```

5. 生成管理员密码哈希（只在本机终端输入密码，不保存明文）：

   ```powershell
   mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
   $cp = Get-Content target/classpath.txt -Raw
   java -cp "target\classes;$cp" edu.uemwiki.contrib.util.PasswordHash
   ```

   终端会提示输入密码，并输出 BCrypt 值；将哈希填入 `ADMIN_PASSWORD_HASH`。IDEA 控制台未提供 `System.console()` 时使用标准输入。应用只接受 BCrypt 哈希登录配置，不提供注册接口。

6. 在 IDEA 运行 `edu.uemwiki.contrib.DemoApplication`。另开终端启动前端：

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

   访问 `http://127.0.0.1:5173/` 投稿，管理员界面访问 `http://127.0.0.1:5173/admin/login`。Vite 将 `/api` 代理到 Spring Boot `8090` 端口；页面通过 REST JSON API 和同源会话 Cookie 通信，附件下载接口返回文件流。

> 也可在 `application.yml` 中用 `${DB_PASSWORD}` 等环境变量引用上述值。示例文件不含真实凭据。

## 浏览器预览（无需真实数据库）

这是本机演示 profile，使用 H2 内存库和一条虚构投稿，只监听 `127.0.0.1`，不会连接 MySQL 或 GitHub。IDEA 运行配置可将 Active profiles 设为 `demo`；或在本目录执行：

```powershell
mvn -q -DskipTests package
java -jar target\contribution-demo-0.1.0.jar --spring.profiles.active=demo
```

再开一个终端运行 `cd frontend; npm ci; npm run dev`，浏览器访问 `http://127.0.0.1:5173/` 查看 Vue 投稿端，访问 `http://127.0.0.1:5173/admin/login` 查看 Vue 管理端（演示账号 `demo`，密码 `admin123`）。审核通过会在 `export/` 写入静态页面和演示附件。该固定演示密码只用于回环地址监听的预览 profile，切勿将 demo profile 用于部署。

投稿 API 为 `POST /api/contributions`，采用 `multipart/form-data`，JSON 响应统一为 `{ "success": true, "message": "...", "data": { ... } }`。管理员附件下载端点保持文件流响应。

## 文件位置和发布

- 私有附件：`UPLOAD_DIR`，默认 `./data/private-uploads`。随机 UUID 文件名；数据库只存唯一文件名、原始文件名和元数据。不要将此目录配置在 Wiki 静态根目录内。
- 本地静态导出：`EXPORT_DIR`，默认 `./export`。页面为 `docs/submissions/<ID>/index.html`，附件同目录；目录页为 `docs/submissions/index.html`。相对导出根目录运行静态服务器即可独立访问，不依赖应用、数据库或登录。
- 静态页会复制 Wiki 的 `assets/theme.css` 与 `assets/site.css`，使用 Wiki 现有配色和文章容器。
- 本地预览导出内容：`python -m http.server 8080 --directory $env:EXPORT_DIR`，访问 `http://localhost:8080/docs/submissions/`。停止 Spring Boot 后仍可这样独立预览。
- 管理详情页的“导出 / 重试”会确定性覆盖相同投稿 ID 的目标文件，不会创建副本；状态分为审核和发布两组。导出失败保存错误并允许重试。
- 配置独立测试仓库后，设置 `GITHUB_REPO=owner/test-repo`、`GITHUB_BRANCH` 和仅有该测试仓库写权限的 `GITHUB_TOKEN`。同一 commit 写入稳定页面、附件和投稿目录索引。不要配置正式仓库。
- PowerShell 示例：`$env:GITHUB_REPO="your-account/uem-wiki-contrib-test"`; `$env:GITHUB_BRANCH="main"`; `$env:GITHUB_TOKEN="github_pat_..."`。Token 仅需测试仓库 Contents 写入权限；不要写入 `application.yml`、日志或 Git。
- 私有附件只能由登录管理员从 `/api/admin/contributions/{ID}/attachment` 下载；公开静态附件仅在审核通过并发布后出现。所有管理员 REST API 都在后端校验管理员会话，变更操作同时校验 CSRF token。

如果数据库在可选附件调整之前已经初始化，请执行 `src/main/resources/db/optional-attachment-migration.sql`；新库直接执行 `db/init.sql` 即可。附件元数据列允许 NULL，投稿正文及其他必填信息仍保存到原有投稿表。

## 安全和范围

上传限制 10 MiB 且仅允许 PDF/DOCX/PPTX 扩展名；文件保存名由服务端生成并规范化路径。联系方式只出现在管理员详情。标题、正文、署名、课程、文件名均在 HTML 中转义。生产部署还应通过反向代理启用 HTTPS、限制管理员访问来源并妥善备份数据库及私有附件。

本 Demo 不转换 Office/PDF 内容、不做在线预览、投稿后修改、注册、用户账户或正式 Wiki 发布。
