# 讯集开发说明

- 项目使用原生 Android Java、Android SDK API 35 和最低 API 26，无 Gradle 或外部服务器依赖。
- `src/com/mutang/xunxi/` 为应用代码；`Collector.java` 处理静态网页、RSS、Atom 解析，`Store.java` 负责本地存储，`RefreshJob.java` 负责后台抓取和通知，`SiteIcons.java` 负责图标缓存，`MainActivity.java` 负责界面。
- `tests/CollectorTest.java` 为解析器回归测试；先运行 `bash fetch-deps.sh` 再按 README 执行测试。
- 不要提交签名密钥、账号凭据、个人订阅数据或生成的 APK；保留包名、存储字段及升级兼容性。
- 修改应用版本时同步更新 AndroidManifest.xml、build.sh、README.md 和应用内版本显示。
- 网络请求仅面向用户订阅的公开站点，不绕过登录、验证码或访问限制。
- `ArticleReader` 只提取并清理网页内容，`ReaderActivity` 默认应用内阅读，提取失败才打开原文。`UpdateClient`、`AppUpdater`、`UpdateProvider` 负责 GitHub Releases 更新；保持 HTTPS、APK 签名比对、版本检查及系统安装确认。
- 正式更新使用 GitHub Release 资产，不将 APK 提交到源代码分支。发布时标签 `vX.Y.Z`、资产 `Xunji-X.Y.Z.apk`、Android 版本号与版本代码须一致。
- `CloudSyncClient` 使用 HTTPS WebDAV 和条件写入，`SyncMerge` 合并订阅、历史、已读及删除记录，`CloudSettings` 的设备本地凭据不得进入同步数据。嵌入互动网页不注入 JavaScript 桥接或替用户点赞、评论。
