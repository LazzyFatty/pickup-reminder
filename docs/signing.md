# APK 打包与签名

## 下载 GitHub Actions 产物

每次推送分支时会自动打包，也可以进入 [Actions](https://github.com/LazzyFatty/pickup-reminder/actions) 手动运行 **Build and sign APK**。打开对应提交的成功运行，在 **Artifacts** 下载压缩包。

- `pickup-<短提交号>.apk` 是测试包，例如 `pickup-8fa738e.apk`。文件名取本次构建提交的前 7 位。测试包使用临时密钥签名，重新安装另一份测试包时，通常需要先卸载旧包。卸载会删除应用里的取件记录。
- `pickup-release.apk` 使用原正式密钥签名。覆盖安装要求证书与手机内版本一致，且 `versionCode` 不低于手机内版本。

下载包内的 `SHA256SUMS.txt` 可用于检查 APK 是否完整。对应源码是 Actions 运行页关联的提交。

## 启用正式自动签名

在仓库 [Settings → Environments](https://github.com/LazzyFatty/pickup-reminder/settings/environments) 中打开 **release-signing**，添加两项 **Environment secrets**。该环境只允许 `main` 分支使用。

| 名称 | 值 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | 原发布用 PKCS12 密钥库的单行 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | 原密钥库密码。密钥别名为 `pickup`，私钥使用同一密码 |

也可在已登录 GitHub CLI 的 PowerShell 中从项目根目录运行。第一条命令通过管道传入密钥库，第二条命令会提示输入密码。将密钥库及密码的离线备份分开保管。Base64 与密钥库本身同样敏感。

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path '.signing/pickup-release.p12').Path)) | gh secret set ANDROID_KEYSTORE_BASE64 --env release-signing --repo LazzyFatty/pickup-reminder
gh secret set ANDROID_KEYSTORE_PASSWORD --env release-signing --repo LazzyFatty/pickup-reminder
```

配置后到 **Actions → Build and sign APK → Run workflow** 选择 `main`。流程会核对生成包的证书指纹，首次成功后自动发布当前版本。

以后发布新版本时，在 `app/build.gradle` 中提高 `versionName` 和 `versionCode`，再推送到 `main`。例如 `versionName '1.1.2'` 会创建 `v1.1.2` 标签和 Release。标签指向通过验签的提交。相同版本的后续提交仍会生成 Actions 产物，现有 Release 保留原来的 APK 和源码。

## 本地打包

在 JDK 17、Android SDK Platform 35 和 Build Tools 34.0.0 环境中运行以下命令。Windows 用 `gradlew.bat`，并使用 SDK Build Tools 目录下的 `zipalign.exe` 和 `apksigner.bat`。

```sh
./gradlew :app:assembleRelease :app:parserCheck :app:lintRelease
zipalign -f 4 app/build/outputs/apk/release/app-release-unsigned.apk pickup-aligned.apk
apksigner sign --ks /path/to/pickup-release.p12 --ks-key-alias pickup --out pickup-release.apk pickup-aligned.apk
apksigner verify --print-certs pickup-release.apk
zipalign -c 4 pickup-release.apk
```

`apksigner` 会提示输入原密钥库密码。向手机覆盖安装前，可核对 APK 的签名证书 SHA-256 指纹。首次构建自己的分发版本，可用 `keytool` 建立自己的密钥库，并单独备份密钥与密码。
