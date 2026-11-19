# 讯集开发说明

- 项目使用原生 Android Java、Android SDK API 35 和最低 API 26，无 Gradle 或外部服务器依赖。
- `src/com/mutang/xunxi/` 为应用代码；`Collector.java` 处理静态网页、RSS、Atom 解析，`Store.java` 负责本地存储，`RefreshJob.java` 负责后台抓取和通知，`SiteIcons.java` 负责图标缓存，`MainActivity.java` 负责界面。
- `tests/CollectorTest.java` 为解析器回归测试；先运行 `bash fetch-deps.sh` 再按 README 执行测试。
- 不要提交签名密钥、账号凭据、个人订阅数据或生成的 APK；保留包名、存储字段及升级兼容性。
- 修改应用版本时同步更新 AndroidManifest.xml、build.sh、README.md 和应用内版本显示。
- 网络请求仅面向用户订阅的公开站点，不绕过登录、验证码或访问限制。
