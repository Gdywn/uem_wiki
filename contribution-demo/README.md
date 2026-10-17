# 应大 Wiki 资料投稿 Demo

该单体应用适用于现有静态 Wiki。投稿和审核数据保存在 MySQL，待审附件保存在私有磁盘目录；审核通过后，本地导出会按 `majorId` 将附件放入资料库学院、专业目录，并更新资料库主页使用的静态资料索引，不为每份投稿生成介绍页。GitHub 远程发布仍是旧的 `docs/submissions` 路径流程，尚未适配新的资料库目录；本次不向远程推送。本 Demo 会拒绝向 `uem-wiki/uem_wiki` 正式仓库发布。

## 版本和依赖

- JDK 8
- Spring Boot 2.7.18
- MyBatis-Plus 3.5.5
- MySQL 8.0+
- Maven 3.6+
- Node.js 20+
- Python 3.7+（本机独立静态预览；Wiki 原构建器也使用 Python）

项目原 Wiki 继续使用 Python 3.7+ 构建 Markdown；提供的 `build_wiki.py` 是 Markdown 转 HTML 构建器，不是 Word 转换器。Java `StaticPublisher` 从后端 `catalog.json` 解析 `majorId`，复制附件到稳定的资料库分类目录，并保留 Wiki 的 `docs/资料库/index.html` 及相关样式资源。投稿附件元数据写入本地静态 `assets/materials-index.json`，主页可通过该索引显示原始文件名和下载链接。投稿正文仍按安全转义后的纯文本处理，DOCX/PDF/PPTX 保持下载附件，不转换文档内容。

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
   $env:EXPORT_DIR="D:\YingDaWiki\uem_wiki"
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

再开一个终端运行 `cd frontend; npm ci; npm run dev`，浏览器访问 `http://127.0.0.1:5173/` 查看 Vue 投稿端，访问 `http://127.0.0.1:5173/admin/login` 查看 Vue 管理端（演示账号 `demo`，密码 `admin123`）。审核通过会把静态附件写入 Wiki 根目录 `docs/资料库/` 下对应分类，并更新 Wiki 根目录 `assets/materials-index.json`；资料库主页仍为 `docs/资料库/index.html`。该固定演示密码只用于回环地址监听的预览 profile，切勿将 demo profile 用于部署。

投稿 API 为 `POST /api/contributions`，采用 `multipart/form-data`，JSON 响应统一为 `{ "success": true, "message": "...", "data": { ... } }`。管理员附件下载端点保持文件流响应。

## 学院与专业分类

分类源目录为 Wiki 根目录下的 `docs/资料库/<学院名>/<专业名>/`；只依据目录识别专业，专业目录即使只有 `.gitkeep` 也会进入分类。只有包含专业子目录的顶层目录会作为有效学院；无专业子目录的旧目录会保留为停用分类并提示。前端不维护副本，通过 `GET /api/catalog` 读取后端资源中的同一份 `src/main/resources/catalog.json`。

在项目目录运行可重复生成脚本：

```powershell
.\scripts\generate_catalog.ps1
```

脚本按目录名生成稳定 ID，并在重复运行时保留现有 ID。目录消失或改名会显示差异，旧分类继续保留但标记为停用，避免已保存的 `major_id` 失去历史名称；新增目录会生成新 ID。修改分类目录并重新生成后，重启 Spring Boot 以加载更新后的 classpath JSON。

已有 MySQL 数据库需要在应用实际使用的数据库中执行一次 `src/main/resources/db/major-id-migration.sql`。该脚本只添加可空 `major_id` 列，不清理或改写已有投稿；旧记录的 `major_id` 保持 NULL，管理员页面显示“未分类”。新建数据库使用已更新的 `db/init.sql`。H2 Demo 的 `db/schema-h2.sql` 也已加入该列。**本次未连接或修改本地数据库**；执行增量脚本前，请先确认目标库和该列尚未存在。

投稿时前端把所选专业的稳定 `majorId` 与其他表单字段放进同一 multipart 请求；后端从 `catalog.json` 校验该 ID 是启用专业后，才把 `major_id` 写入 `contribution`。学院名称通过专业所属关系解析，不另存学院 ID。管理员列表和详情按 ID 显示学院、专业名称；旧记录或目录中无法识别的 ID 显示“未分类”。课程名称字段保持原样。
## 文件位置和发布

- 私有附件：`UPLOAD_DIR`，默认 `./data/private-uploads`。随机 UUID 文件名；数据库只存唯一文件名、原始文件名和元数据。不要将此目录配置在 Wiki 静态根目录内。
- 本地静态导出：`EXPORT_DIR`，默认与 `WIKI_ROOT` 相同（本机为 `D:\YingDaWiki\uem_wiki`）。普通分类附件位于 `docs/资料库/<学院目录>/<专业目录>/<投稿ID>/<安全文件名>`；通用资料位于 `docs/资料库/通用资料/<投稿ID>/<安全文件名>`。资料库主页为 `docs/资料库/index.html`，下载索引为 `assets/materials-index.json`。导出不生成每份投稿的介绍 HTML，静态文件不依赖应用、数据库或登录。
- 静态导出会复制 Wiki 的资料库主页和所需公开资源；已存在的本地资料库主页及资料索引条目会保留，重试只按稳定投稿 ID 更新对应索引记录和附件文件。
- 本地预览导出内容：`python -m http.server 8080 --directory $env:EXPORT_DIR`，访问 `http://localhost:8080/docs/资料库/`。停止 Spring Boot 后仍可通过静态服务器访问主页和附件。
- 管理详情页的“导出 / 重试”会确定性覆盖相同投稿 ID 的目标文件，不会创建副本；状态分为审核和发布两组。导出失败保存错误并允许重试。
- GitHub 远程发布逻辑尚未适配新的资料库目录：当前 `GitHubPublisher` 仍查找并发布 `docs/submissions/<ID>/`。后续需将它改为提交 `docs/资料库/...` 附件及主页索引后，再配置独立测试仓库；本次不要向远程推送，也不要配置正式仓库。
- PowerShell 示例：`$env:GITHUB_REPO="your-account/uem-wiki-contrib-test"`; `$env:GITHUB_BRANCH="main"`; `$env:GITHUB_TOKEN="github_pat_..."`。Token 仅需测试仓库 Contents 写入权限；不要写入 `application.yml`、日志或 Git。
- 私有附件只能由登录管理员从 `/api/admin/contributions/{ID}/attachment` 下载；公开静态附件仅在审核通过并发布后出现。所有管理员 REST API 都在后端校验管理员会话，变更操作同时校验 CSRF token。

如果数据库在可选附件调整之前已经初始化，请执行 `src/main/resources/db/optional-attachment-migration.sql`；新库直接执行 `db/init.sql` 即可。附件元数据列允许 NULL，投稿正文及其他必填信息仍保存到原有投稿表。

## 安全和范围

上传限制 10 MiB 且仅允许 PDF/DOCX/PPTX 扩展名；文件保存名由服务端生成并规范化路径。联系方式只出现在管理员详情。标题、正文、署名、课程、文件名均在 HTML 中转义。生产部署还应通过反向代理启用 HTTPS、限制管理员访问来源并妥善备份数据库及私有附件。

本 Demo 不转换 Office/PDF 内容、不做在线预览、投稿后修改、注册、用户账户或正式 Wiki 发布。
